#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Pause the student AKS cluster to save credit, keeping the data.
#
#   bash scripts/aks/pause.sh
#
# 1. Backs up all 9 databases with scripts/db/backup-aks.sh (aborts if that fails).
# 2. Stops the cluster (az aks stop): the node VM is deallocated, compute billing stops.
#
# Why the backup is mandatory: on `az aks start` AKS can give you a NEW node VM. The
# student profile keeps PostgreSQL/RabbitMQ/Redis on that node's own disk (local-path),
# so the data does not come back. scripts/aks/resume.sh starts the cluster, rebuilds the
# volumes and restores this backup.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail
export MSYS_NO_PATHCONV=1

RG="${AKS_RESOURCE_GROUP:-rg-swiftdrop-prod}"
AKS="${AKS_CLUSTER:-swiftdrop-aks}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

state="$(az aks show -g "$RG" -n "$AKS" --query powerState.code -o tsv)"
if [ "$state" != "Running" ]; then
  echo "Cluster $AKS is already $state; nothing to do."; exit 0
fi

ctx="$(kubectl config current-context)"
if [ "$ctx" != "$AKS" ]; then
  echo "kubectl points at '$ctx', not '$AKS'. Run: az aks get-credentials -g $RG -n $AKS" >&2; exit 1
fi

if kubectl get ns swiftdrop-data >/dev/null 2>&1; then
  echo "== 1/2 Backing up databases"
  bash "$ROOT/scripts/db/backup-aks.sh"
  latest="$(ls -d "$ROOT"/backups/*/ | sort | tail -1)"
  echo "Backup: ${latest#$ROOT/}"
else
  echo "== 1/2 SwiftDrop is not deployed; no backup needed"
fi

echo "== 2/2 Stopping $AKS (takes a few minutes)"
az aks stop -g "$RG" -n "$AKS"
echo "Stopped. Resume with: bash scripts/aks/resume.sh"
