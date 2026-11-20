#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Back up every SwiftDrop PostgreSQL database from the cluster to your laptop.
#
#   bash scripts/db/backup-aks.sh                # all 9 databases
#   bash scripts/db/backup-aks.sh iam order      # only these
#
# Writes backups/<UTC timestamp>/<key>.dump (pg_dump custom format, compressed)
# plus a manifest with each file's SHA-256. Each dump is checksum-verified and retried up to
# 3 times, so a dropped connection cannot leave a silently truncated backup.
# backups/ is git-ignored: the dumps contain real user data.
#
# Needs kubectl pointed at the cluster (az aks get-credentials ...). The dump is
# taken inside each postgres pod over its local socket, so no port-forward and no
# password are needed. Safe to run while the app is live: pg_dump reads a
# consistent snapshot without blocking writers.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail
# Git Bash on Windows rewrites "/tmp/..." arguments into Windows paths; the paths below are
# inside the Linux pod, so turn that conversion off.
export MSYS_NO_PATHCONV=1

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

# One attempt: dump inside the pod to a temp file, hash it there, stream it down and compare
# the hash locally. A connection dropped mid-transfer leaves a truncated file that still starts
# with "PGDMP", so only the checksum proves the copy is complete.
dump_once() {
  local key="$1" pod="$2" db="$3" user="$4" file="$5" tmp="/tmp/swiftdrop-backup-$key.dump"
  kubectl exec -n "$NS" "$pod" -c postgres --     pg_dump -U "$user" -d "$db" --format=custom --no-owner --no-privileges -f "$tmp" || return 1
  local remote_sum local_sum
  remote_sum="$(kubectl exec -n "$NS" "$pod" -c postgres -- sha256sum "$tmp" | cut -d' ' -f1)" || return 1
  kubectl exec -n "$NS" "$pod" -c postgres -- cat "$tmp" > "$file" || return 1
  local_sum="$(sha256sum "$file" | cut -d' ' -f1)"
  [ -n "$remote_sum" ] && [ "$remote_sum" = "$local_sum" ] && [ "$(head -c 5 "$file")" = "PGDMP" ]
}

failed=0
for key in "${KEYS[@]}"; do
  pod="${key}-postgres-0"
  file="$OUT/$key.dump"
  ok=0
  for attempt in 1 2 3; do
    # Database name and user come from the pod's own environment, so they always match the chart.
    if db="$(kubectl exec -n "$NS" "$pod" -c postgres -- printenv POSTGRES_DB 2>/dev/null)"        && user="$(kubectl exec -n "$NS" "$pod" -c postgres -- printenv POSTGRES_USER 2>/dev/null)"        && dump_once "$key" "$pod" "$db" "$user" "$file" 2>/dev/null; then
      ok=1; break
    fi
    [ "$attempt" -lt 3 ] && { echo "  … $key: attempt $attempt failed (connection or dump), retrying"; sleep $((attempt * 5)); }
  done
  kubectl exec -n "$NS" "$pod" -c postgres -- rm -f "/tmp/swiftdrop-backup-$key.dump" >/dev/null 2>&1 || true

  if [ "$ok" -eq 1 ]; then
    printf '  ✓ %-10s %-22s %8s  sha256 verified
' "$key" "$db" "$(du -h "$file" | cut -f1)"
    printf '%s	%s	%s	%s
' "$key" "$db" "$(wc -c < "$file" | tr -d ' ')" "$(sha256sum "$file" | cut -d' ' -f1)" >> "$OUT/MANIFEST.tsv"
  else
    rm -f "$file"
    echo "  ✗ $key: failed 3 times (is the cluster running? is your connection stable?)"; failed=1
  fi
done

echo
if [ "$failed" -eq 0 ]; then
  echo "Done. Restore with: bash scripts/db/restore-aks.sh backups/$STAMP [keys...]"
else
  echo "Finished with errors; the backups listed with ✓ are usable." >&2
  exit 1
fi
