#!/usr/bin/env bash
# Seed the LOCAL Kubernetes cluster (Docker Desktop) stage by stage.
#
# A 4-CPU laptop cannot run all ten Java services at once, so this script switches
# services on only for the stages that need them. Events published while a consumer
# is switched off wait in RabbitMQ's durable queues (24 h TTL) and are processed when
# it starts again. See scripts/seed/README.md.
#
#   SEED_PASSWORD='Demo-pass-2026' scripts/seed/run-local.sh            # all stages
#   SEED_PASSWORD='Demo-pass-2026' scripts/seed/run-local.sh deliver    # one stage again
#
# Stages: install warmup users orders assign deliver catchup timeshift
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHART="$ROOT/infra/helm/swiftdrop"
SEED="$ROOT/scripts/seed/seed.mjs"
APPS=swiftdrop-apps
MSG=swiftdrop-messaging
PORT="${SEED_PORT:-18080}"
HELM="${HELM:-helm}"

: "${SEED_PASSWORD:?Set SEED_PASSWORD (the password every seeded account will use), e.g. SEED_PASSWORD='Demo-pass-2026'}"
[ -f "$CHART/values-secrets.yaml" ] || { echo "Missing $CHART/values-secrets.yaml (run scripts/generate-helm-secrets.sh)"; exit 1; }
if [ -z "${SEED_ADMIN_PASSWORD:-}" ]; then
  # Read the bootstrap admin password from the git-ignored secrets file (never printed).
  SEED_ADMIN_PASSWORD="$(awk '/bootstrapAdmin:/{f=1} f && /password:/{sub(/.*password:[ ]*/, ""); gsub(/"/, ""); print; exit}' "$CHART/values-secrets.yaml")"
fi
export SEED_PASSWORD SEED_ADMIN_PASSWORD SEED_API="http://127.0.0.1:$PORT"
export SEED_CONCURRENCY="${SEED_CONCURRENCY:-2}"   # gentle on a 4-CPU laptop

log() { printf '\n==> %s\n' "$*"; }

# One service at a time: a booting JVM takes the whole CPU for a minute or two, and
# booting two together starves the services that are already running.
up() {
  for s in "$@"; do
    echo "   starting $s ..."
    kubectl -n "$APPS" scale "deploy/$s" --replicas=1 >/dev/null
    kubectl -n "$APPS" rollout status "deploy/$s" --timeout=600s >/dev/null
  done
}
down() { for s in "$@"; do kubectl -n "$APPS" scale "deploy/$s" --replicas=0 >/dev/null; echo "   stopped $s"; done; }

queues() { kubectl -n "$MSG" exec rabbitmq-0 -- rabbitmqctl -q list_queues name messages 2>/dev/null; }

# Wait until every named queue is empty (its consumer has processed the backlog).
drain() {
  local pending
  for _ in $(seq 1 120); do
    pending="$(queues | awk -v want=" $* " 'index(want, " " $1 " ") { s += $2 } END { print s + 0 }')"
    [ "$pending" = "0" ] && { echo "   queues drained: $*"; return 0; }
    echo "   waiting for $pending queued event(s) ..."
    sleep 5
  done
  echo "   timed out waiting for: $*"; return 1
}

PF_PID=""
gateway() {
  # Node cannot resolve *.localhost on Windows, so talk to the gateway through a port-forward.
  if [ -n "$PF_PID" ] && kill -0 "$PF_PID" 2>/dev/null; then return; fi
  kubectl -n "$APPS" port-forward svc/api-gateway "$PORT:8080" >/dev/null 2>&1 &
  PF_PID=$!
  for _ in $(seq 1 30); do
    node -e "fetch('$SEED_API/api/v1/auth/.well-known/jwks.json').then(()=>process.exit(0)).catch(()=>process.exit(1))" 2>/dev/null && return
    sleep 1
  done
  echo "Gateway port-forward on $PORT did not come up"; exit 1
}
trap '[ -n "$PF_PID" ] && kill "$PF_PID" 2>/dev/null || true' EXIT

seed() { gateway; node "$SEED" "$@"; }

stage_install() {
  log "Install the seeding profile (all services created; only gateway + auth running)"
  "$HELM" upgrade --install swiftdrop "$CHART" -n default \
    -f "$CHART/values-local.yaml" -f "$CHART/values-local-seed.yaml" -f "$CHART/values-secrets.yaml" \
    --wait --timeout 20m >/dev/null
  up api-gateway auth-iam-service
  down customer-order-service pricing-zone-service
}

stage_warmup() {
  # A queue exists only after its service has started once; events sent before that are lost.
  log "Warm-up: make sure every consumer queue exists"
  local pair svc queue
  for pair in dispatch-assignment-service:dispatch.order-created.queue \
              delivery-execution-service:delivery.dispatch-assigned.queue \
              notification-service:notification.events.queue \
              analytics-ai-service:analytics.events.queue \
              customer-order-service:customer-order.status-sync.queue; do
    svc="${pair%%:*}"; queue="${pair#*:}"
    if queues | awk '{print $1}' | grep -qx "$queue"; then echo "   $queue exists"; continue; fi
    up "$svc"
    for _ in $(seq 1 30); do queues | awk '{print $1}' | grep -qx "$queue" && break; sleep 2; done
    down "$svc"
  done
}

stage_users() {
  log "Users: admin creates dispatchers + couriers, customers register"
  seed users
}

stage_orders() {
  log "Orders: customers book parcels, merchants are approved and send B2B orders (dispatch is off; its events wait in the queue)"
  up customer-order-service pricing-zone-service
  seed orders
  # Merchant B2B orders are forwarded to order-service (which prices them), so all three run here.
  up merchant-b2b-service
  seed merchants
  down merchant-b2b-service pricing-zone-service
}

stage_assign() {
  log "Assign: dispatch files the bookings, couriers start shifts, dispatchers assign"
  up customer-order-service dispatch-assignment-service
  # Shifts must start only after dispatch has filed every booking as UNASSIGNED,
  # otherwise its auto-engine assigns them all and the dispatcher board stays empty.
  drain dispatch.order-created.queue
  seed shifts cancel
  drain dispatch.order-cancelled.queue
  seed assign
  down dispatch-assignment-service customer-order-service
}

stage_deliver() {
  log "Deliver: couriers move parcels along, with proof-of-delivery photos"
  up delivery-execution-service
  drain delivery.dispatch-assigned.queue
  seed deliver
  down delivery-execution-service
}

stage_catchup() {
  log "Catch up: order, dispatch, notification and analytics process the queued events"
  up customer-order-service dispatch-assignment-service
  drain customer-order.status-sync.queue dispatch.parcel-status.queue
  down dispatch-assignment-service
  up notification-service analytics-ai-service
  drain notification.events.queue analytics.events.queue
  seed notifications summary
  down notification-service analytics-ai-service
}

stage_timeshift() {
  # Runs once, last: spreads the seeded history over the past month in every database
  # (only needs the PostgreSQL pods, which the seeding profile keeps running).
  log "Time-shift: spread the seeded history over the past 30 days"
  node "$ROOT/scripts/seed/timeshift.mjs" | grep -v "#"
}

STAGES=("$@")
[ ${#STAGES[@]} -eq 0 ] && STAGES=(install warmup users orders assign deliver catchup timeshift)
for s in "${STAGES[@]}"; do
  case "$s" in
    install|warmup|users|orders|assign|deliver|catchup|timeshift) "stage_$s" ;;
    *) echo "Unknown stage: $s (install warmup users orders assign deliver catchup timeshift)"; exit 2 ;;
  esac
done

log "Done. Back to the everyday local profile (seeded data stays in the volumes):"
echo "   helm upgrade swiftdrop infra/helm/swiftdrop -n default -f infra/helm/swiftdrop/values-local.yaml -f infra/helm/swiftdrop/values-secrets.yaml"
