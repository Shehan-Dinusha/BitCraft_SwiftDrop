#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Resume the student AKS cluster after scripts/aks/pause.sh.
#
#   bash scripts/aks/resume.sh                    # start, repair volumes, restore newest backup
#   bash scripts/aks/resume.sh backups/<stamp>    # ... restore this backup instead
#   bash scripts/aks/resume.sh --no-restore       # ... leave the databases empty (reseed instead)
#
# What it does:
# 1. Starts the cluster and refreshes kubectl credentials.
# 2. If AKS handed out a new node, every local-path volume still points at the old one and
#    its pod stays Pending. For each such volume it scales the owner to 0, deletes the stale
#    claim and volume, and scales back up so a fresh, empty volume is created on the new
#    node. (If the node survived, nothing is touched and no restore is needed.)
# 3. Restores the databases with scripts/db/restore-aks.sh (asks you to type "restore").
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail
export MSYS_NO_PATHCONV=1

RG="${AKS_RESOURCE_GROUP:-rg-swiftdrop-prod}"
AKS="${AKS_CLUSTER:-swiftdrop-aks}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DATA_NS=swiftdrop-data; MSG_NS=swiftdrop-messaging; APPS_NS=swiftdrop-apps
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

RESTORE=auto; BACKUP=""
case "${1:-}" in
  --no-restore) RESTORE=no ;;
  "") ;;
  *) BACKUP="$1"; [ -d "$BACKUP" ] || { echo "No such backup folder: $BACKUP" >&2; exit 1; } ;;
esac

echo "== 1/3 Starting $AKS (takes a few minutes)"
state="$(az aks show -g "$RG" -n "$AKS" --query powerState.code -o tsv)"
[ "$state" = "Running" ] || az aks start -g "$RG" -n "$AKS"
az aks get-credentials -g "$RG" -n "$AKS" --overwrite-existing >/dev/null
kubectl wait --for=condition=Ready node --all --timeout=10m >/dev/null
nodes="$(kubectl get nodes -o jsonpath='{.items[*].metadata.name}')"
echo "Node(s): $nodes"

if ! kubectl get ns "$DATA_NS" >/dev/null 2>&1; then
  echo "SwiftDrop is not deployed yet; nothing else to do."; exit 0
fi

echo "== 2/3 Checking local-path volumes"
# PV name, claim namespace/name and the node the volume lives on.
stale=()
while read -r pv ns claim node; do
  [ -n "$pv" ] || continue
  if [[ " $nodes " != *" $node "* ]]; then stale+=("$ns/$claim/$pv"); fi
done < <(kubectl get pv -o json | python -c "
import json, sys
for pv in json.load(sys.stdin)['items']:
    if pv['spec'].get('storageClassName') != 'local-path': continue
    ref = pv['spec'].get('claimRef') or {}
    try:
        node = pv['spec']['nodeAffinity']['required']['nodeSelectorTerms'][0]['matchExpressions'][0]['values'][0]
    except (KeyError, IndexError):
        continue
    print(pv['metadata']['name'], ref.get('namespace', '-'), ref.get('name', '-'), node)
")

if [ "${#stale[@]}" -eq 0 ]; then
  echo "All volumes are on the current node: the data survived. No repair or restore needed."
  exit 0
fi
echo "${#stale[@]} volume(s) belong to a node that no longer exists; rebuilding them empty."

# Owners: StatefulSet volume claims are "<template>-<sts>-0"; the delivery photo volume is
# a chart-managed claim used by a Deployment.
declare -A sts_scaled=()
for item in "${stale[@]}"; do
  IFS=/ read -r ns claim pv <<< "$item"
  if [ "$ns/$claim" = "$APPS_NS/delivery-execution-service-data" ]; then
    owner_kind=deploy; owner=delivery-execution-service
  else
    owner_kind=sts; owner="$(sed -E 's/^[a-z]+-(pgdata|data)-//; s/-0$//' <<< "$claim")"
  fi
  key="$ns/$owner_kind/$owner"
  if [ -z "${sts_scaled[$key]:-}" ]; then
    kubectl scale "$owner_kind/$owner" -n "$ns" --replicas=0 >/dev/null
    sts_scaled[$key]=1
  fi
  if [ "$owner_kind" = deploy ]; then
    # Keep the claim's spec so it can be recreated after deletion (Helm will not re-create it
    # until the next deploy, and the pod cannot start without it).
    kubectl get pvc "$claim" -n "$ns" -o json | python -c "
import json, sys
p = json.load(sys.stdin)
keep = {k: v for k, v in p['metadata'].items() if k in ('name', 'namespace', 'labels', 'annotations')}
keep.get('annotations', {}).pop('pv.kubernetes.io/bind-completed', None)
keep.get('annotations', {}).pop('pv.kubernetes.io/bound-by-controller', None)
keep.get('annotations', {}).pop('volume.kubernetes.io/selected-node', None)
spec = {k: v for k, v in p['spec'].items() if k not in ('volumeName',)}
print(json.dumps({'apiVersion': 'v1', 'kind': 'PersistentVolumeClaim', 'metadata': keep, 'spec': spec}))
" > "$TMP/pvc-$claim.json"
  fi
  kubectl delete pvc "$claim" -n "$ns" --wait=false >/dev/null
  kubectl delete pv "$pv" --wait=false >/dev/null 2>&1 || true
  echo "  rebuilt: $ns/$claim"
done

for item in "${stale[@]}"; do
  IFS=/ read -r ns claim pv <<< "$item"
  kubectl wait --for=delete "pvc/$claim" -n "$ns" --timeout=120s >/dev/null 2>&1 || true
  if [ -f "$TMP/pvc-$claim.json" ]; then
    # Through stdin: with MSYS_NO_PATHCONV a Windows kubectl cannot open a Git Bash /tmp path.
    kubectl apply -f - < "$TMP/pvc-$claim.json" >/dev/null
    rm -f "$TMP/pvc-$claim.json"
  fi
done

# local-path tries to clean the old volumes with helper pods pinned to the vanished node;
# they can never run, so remove them.
kubectl get pods -n local-path-storage -o name 2>/dev/null | grep helper-pod | xargs -r kubectl delete -n local-path-storage --wait=false >/dev/null 2>&1 || true

for key in "${!sts_scaled[@]}"; do
  IFS=/ read -r ns kind owner <<< "$key"
  kubectl scale "$kind/$owner" -n "$ns" --replicas=1 >/dev/null
done

echo "Waiting for the data stores to come up empty..."
kubectl wait --for=condition=Ready pod --all -n "$DATA_NS" --timeout=10m >/dev/null
kubectl wait --for=condition=Ready pod --all -n "$MSG_NS" --timeout=10m >/dev/null
# Services that lost their connections while the stores were down reconnect cleanly on restart.
kubectl rollout restart deploy -n "$APPS_NS" >/dev/null

echo "== 3/3 Restoring databases"
if [ "$RESTORE" = no ]; then
  echo "Skipped (--no-restore). The databases are empty; run the seed to refill them."
  exit 0
fi
if [ -z "$BACKUP" ]; then
  BACKUP="$(ls -d "$ROOT"/backups/*/ 2>/dev/null | sort | tail -1)"
  [ -n "$BACKUP" ] || { echo "No backup found in backups/. Reseed, or pass a folder." >&2; exit 1; }
fi
echo "Using ${BACKUP#$ROOT/}"
bash "$ROOT/scripts/db/restore-aks.sh" "${BACKUP%/}"
