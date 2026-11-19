#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Back up every SwiftDrop PostgreSQL database from the cluster to your laptop.
#
#   bash scripts/db/backup-aks.sh                # all 9 databases
#   bash scripts/db/backup-aks.sh iam order      # only these
#
# Writes backups/<UTC timestamp>/<key>.dump (pg_dump custom format, compressed)
# plus a manifest. backups/ is git-ignored: the dumps contain real user data.
#
# Needs kubectl pointed at the cluster (az aks get-credentials ...). The dump is
# taken inside each postgres pod over its local socket, so no port-forward and no
# password are needed. Safe to run while the app is live: pg_dump reads a
# consistent snapshot without blocking writers.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

NS="${SWIFTDROP_DATA_NS:-swiftdrop-data}"
ALL_KEYS=(iam order pricing dispatch delivery merchant webhook notify analytics)
if [ "$#" -gt 0 ]; then KEYS=("$@"); else KEYS=("${ALL_KEYS[@]}"); fi

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$ROOT/backups/$STAMP"
mkdir -p "$OUT"

echo "Context: $(kubectl config current-context)   namespace: $NS"
echo "Writing to backups/$STAMP"
echo

failed=0
for key in "${KEYS[@]}"; do
  pod="${key}-postgres-0"
  # Database name and user come from the pod's own environment, so they always match the chart.
  if ! db="$(kubectl exec -n "$NS" "$pod" -c postgres -- printenv POSTGRES_DB 2>/dev/null)"; then
    echo "  ✗ $key: pod $pod not reachable (is the cluster running?)"; failed=1; continue
  fi
  user="$(kubectl exec -n "$NS" "$pod" -c postgres -- printenv POSTGRES_USER)"
  file="$OUT/$key.dump"

  if kubectl exec -n "$NS" "$pod" -c postgres -- \
       pg_dump -U "$user" -d "$db" --format=custom --no-owner --no-privileges > "$file"; then
    # A custom-format dump starts with the magic bytes "PGDMP".
    if [ "$(head -c 5 "$file")" = "PGDMP" ]; then
      printf '  ✓ %-10s %-22s %8s\n' "$key" "$db" "$(du -h "$file" | cut -f1)"
      printf '%s\t%s\t%s\n' "$key" "$db" "$(wc -c < "$file" | tr -d ' ')" >> "$OUT/MANIFEST.tsv"
    else
      echo "  ✗ $key: output is not a pg_dump archive"; failed=1
    fi
  else
    echo "  ✗ $key: pg_dump failed"; failed=1
  fi
done

echo
if [ "$failed" -eq 0 ]; then
  echo "Done. Restore with: bash scripts/db/restore-aks.sh backups/$STAMP [keys...]"
else
  echo "Finished with errors; the backups listed with ✓ are usable." >&2
  exit 1
fi
