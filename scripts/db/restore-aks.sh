#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Restore SwiftDrop databases from a backup made by backup-aks.sh.
#
#   bash scripts/db/restore-aks.sh backups/20261120T101500Z            # all in the folder
#   bash scripts/db/restore-aks.sh backups/20261120T101500Z iam order  # only these
#
# DESTRUCTIVE: each database is replaced by the backup (tables are dropped and
# recreated). For every database the owning service is scaled to 0 first, so
# nothing writes during the restore, then scaled back to its previous size.
# You are asked to type "restore" before anything changes.
#
# Restore all databases from the SAME backup folder: the services reference each
# other's ids, so mixing points in time leaves orphaned references.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DATA_NS="${SWIFTDROP_DATA_NS:-swiftdrop-data}"
APPS_NS="${SWIFTDROP_APPS_NS:-swiftdrop-apps}"

DIR="${1:?usage: restore-aks.sh <backup-folder> [keys...]}"
shift
[ -d "$DIR" ] || { echo "No such folder: $DIR" >&2; exit 1; }

declare -A SERVICE=(
  [iam]=auth-iam-service [order]=customer-order-service [pricing]=pricing-zone-service
  [dispatch]=dispatch-assignment-service [delivery]=delivery-execution-service
  [merchant]=merchant-b2b-service [webhook]=webhook-resiliency-service
  [notify]=notification-service [analytics]=analytics-ai-service
)

if [ "$#" -gt 0 ]; then
  KEYS=("$@")
else
  KEYS=()
  for f in "$DIR"/*.dump; do [ -e "$f" ] && KEYS+=("$(basename "$f" .dump)"); done
fi
[ "${#KEYS[@]}" -gt 0 ] || { echo "No .dump files in $DIR" >&2; exit 1; }

for key in "${KEYS[@]}"; do
  [ -n "${SERVICE[$key]:-}" ] || { echo "Unknown database key: $key" >&2; exit 1; }
  [ -f "$DIR/$key.dump" ] || { echo "Missing $DIR/$key.dump" >&2; exit 1; }
done

echo "Context:   $(kubectl config current-context)"
echo "Backup:    $DIR"
echo "Databases: ${KEYS[*]}"
echo
echo "This REPLACES the current data in those databases with the backup."
read -r -p 'Type "restore" to continue: ' answer
[ "$answer" = "restore" ] || { echo "Cancelled."; exit 1; }
echo

for key in "${KEYS[@]}"; do
  svc="${SERVICE[$key]}"
  pod="${key}-postgres-0"
  db="$(kubectl exec -n "$DATA_NS" "$pod" -c postgres -- printenv POSTGRES_DB)"
  user="$(kubectl exec -n "$DATA_NS" "$pod" -c postgres -- printenv POSTGRES_USER)"
  replicas="$(kubectl get deploy "$svc" -n "$APPS_NS" -o jsonpath='{.spec.replicas}')"

  echo "── $key ($db): stopping $svc (was $replicas replica(s))"
  # With 0 replicas the HorizontalPodAutoscaler stops acting until we scale back up.
  kubectl scale deploy "$svc" -n "$APPS_NS" --replicas=0 >/dev/null
  kubectl wait --for=delete pod -l "app.kubernetes.io/name=$svc" -n "$APPS_NS" --timeout=120s >/dev/null 2>&1 || true

  echo "   restoring..."
  restore_ok=1
  kubectl exec -i -n "$DATA_NS" "$pod" -c postgres -- \
    pg_restore -U "$user" -d "$db" --clean --if-exists --no-owner --no-privileges --single-transaction \
    < "$DIR/$key.dump" || restore_ok=0

  echo "   starting $svc again ($replicas replica(s))"
  kubectl scale deploy "$svc" -n "$APPS_NS" --replicas="${replicas:-1}" >/dev/null

  if [ "$restore_ok" -eq 1 ]; then
    echo "   ✓ restored"
  else
    echo "   ✗ pg_restore failed; --single-transaction rolled it back, so $db is unchanged" >&2
    exit 1
  fi
done

echo
echo "Done. Watch the services come back: kubectl get pods -n $APPS_NS -w"
