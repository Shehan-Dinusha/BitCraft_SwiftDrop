#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Empty every SwiftDrop database on the live cluster, to reseed demo data from scratch.
#
#   bash scripts/db/wipe-aks.sh
#
# DESTRUCTIVE AND IRREVERSIBLE except through the backup it takes first. Every
# account, order, payment and notification is deleted, including real people's.
#
#   1. refuses unless kubectl points at the AKS cluster (AKS_CLUSTER, default swiftdrop-aks);
#   2. backs up all 9 databases with backup-aks.sh and stops if that fails;
#   3. asks you to type "wipe";
#   4. scales the 9 business services to 0, so nothing writes meanwhile;
#   5. empties each database (drops and recreates its public schema), purges every
#      RabbitMQ queue (events about the old data) and flushes Redis (sessions, rate limits);
#   6. scales the services back up: Flyway rebuilds each schema, and auth-iam-service
#      creates the bootstrap admin again because no system admin exists.
#
# Then seed: scripts/seed/README.md, "A cluster that runs everything (AKS)".
# To undo: bash scripts/db/restore-aks.sh backups/<the folder printed in step 2>
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail
export MSYS_NO_PATHCONV=1

AKS="${AKS_CLUSTER:-swiftdrop-aks}"
DATA_NS="${SWIFTDROP_DATA_NS:-swiftdrop-data}"
APPS_NS="${SWIFTDROP_APPS_NS:-swiftdrop-apps}"
MQ_NS="${SWIFTDROP_MESSAGING_NS:-swiftdrop-messaging}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

declare -A SERVICE=(
  [iam]=auth-iam-service [order]=customer-order-service [pricing]=pricing-zone-service
  [dispatch]=dispatch-assignment-service [delivery]=delivery-execution-service
  [merchant]=merchant-b2b-service [webhook]=webhook-resiliency-service
  [notify]=notification-service [analytics]=analytics-ai-service
)
KEYS=(iam order pricing dispatch delivery merchant webhook notify analytics)

ctx="$(kubectl config current-context)"
if [ "$ctx" != "$AKS" ]; then
  echo "kubectl points at '$ctx', not '$AKS'. This script only empties the AKS cluster." >&2
  exit 1
fi
kubectl get ns "$DATA_NS" >/dev/null 2>&1 || { echo "SwiftDrop is not deployed on $AKS." >&2; exit 1; }

echo "== 1/4 Backing up every database first"
bash "$ROOT/scripts/db/backup-aks.sh"
backup="$(ls -d "$ROOT"/backups/*/ | sort | tail -1)"
backup="${backup%/}"
for key in "${KEYS[@]}"; do
  [ -s "$backup/$key.dump" ] || { echo "Backup $backup has no $key.dump; nothing was changed." >&2; exit 1; }
done
echo "Backup: ${backup#$ROOT/}  (restore with: bash scripts/db/restore-aks.sh ${backup#$ROOT/})"
echo

echo "Context:   $ctx"
echo "This DELETES all SwiftDrop data on $AKS: every account, order, payment and notification,"
echo "including real people's. Only the backup above can bring it back."
read -r -p 'Type "wipe" to continue: ' answer
[ "$answer" = "wipe" ] || { echo "Cancelled. Nothing was changed."; exit 1; }
echo

echo "== 2/4 Stopping the services"
declare -A REPLICAS
for key in "${KEYS[@]}"; do
  svc="${SERVICE[$key]}"
  REPLICAS[$key]="$(kubectl get deploy "$svc" -n "$APPS_NS" -o jsonpath='{.spec.replicas}')"
  # With 0 replicas the HorizontalPodAutoscaler stops acting until we scale back up.
  kubectl scale deploy "$svc" -n "$APPS_NS" --replicas=0 >/dev/null
  echo "   stopped $svc (was ${REPLICAS[$key]:-1})"
done
for key in "${KEYS[@]}"; do
  kubectl wait --for=delete pod -l "app.kubernetes.io/name=${SERVICE[$key]}" -n "$APPS_NS" --timeout=180s >/dev/null 2>&1 || true
done

# Whatever happens next, bring the services back up.
restart() {
  echo "== 4/4 Starting the services again"
  for key in "${KEYS[@]}"; do
    kubectl scale deploy "${SERVICE[$key]}" -n "$APPS_NS" --replicas="${REPLICAS[$key]:-1}" >/dev/null
    echo "   started ${SERVICE[$key]} (${REPLICAS[$key]:-1})"
  done
}
trap restart EXIT

echo "== 3/4 Emptying databases, queues and caches"
for key in "${KEYS[@]}"; do
  pod="${key}-postgres-0"
  user="$(kubectl exec -n "$DATA_NS" "$pod" -c postgres -- printenv POSTGRES_USER)"
  # The schema is owned by the service's database user; Flyway recreates every table on start.
  kubectl exec -i -n "$DATA_NS" "$pod" -c postgres -- sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q' <<SQL
DROP SCHEMA public CASCADE;
CREATE SCHEMA public AUTHORIZATION "$user";
GRANT ALL ON SCHEMA public TO "$user";
SQL
  echo "   emptied $key"
done

for queue in $(kubectl exec -n "$MQ_NS" rabbitmq-0 -- rabbitmqctl list_queues -q --no-table-headers name 2>/dev/null | tr -d '\r'); do
  kubectl exec -n "$MQ_NS" rabbitmq-0 -- rabbitmqctl purge_queue "$queue" >/dev/null
done
echo "   purged RabbitMQ queues"

redis_pod="$(kubectl get pod -n "$MQ_NS" -l app.kubernetes.io/name=redis -o name | head -1)"
if [ -n "$redis_pod" ]; then
  kubectl exec -n "$MQ_NS" "$redis_pod" -- redis-cli FLUSHALL >/dev/null
  echo "   flushed Redis"
fi

echo
echo "Done once the services are back: kubectl get pods -n $APPS_NS -w"
echo "Sign in as the bootstrap admin, then seed (scripts/seed/README.md, AKS section)."
