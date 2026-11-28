#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Print a webhook secret keyring (WEBHOOK_SECRET_KEYRING) on one line:
#   {"activeKeyId":"v1","keys":{"v1":"<standard Base64 of 32 random bytes>"}}
#
# webhook-resiliency-service encrypts merchant webhook signing secrets with AES-256-GCM under the active key
# and stores the key id with each secret. The service refuses to start without a valid keyring; there is no
# generated fallback key.
#
# Usage (Git Bash on Windows, or any Linux/macOS shell with openssl):
#   bash scripts/generate-webhook-keyring.sh            # new keyring with key v1
#   bash scripts/generate-webhook-keyring.sh v2         # new keyring whose only key is v2
#   bash scripts/generate-webhook-keyring.sh --key      # one new key only (to add to an existing keyring)
#
# Local Docker Compose: put the output in .env, single-quoted:
#   WEBHOOK_SECRET_KEYRING='{"activeKeyId":"v1","keys":{"v1":"..."}}'
# AKS: scripts/generate-helm-secrets.sh writes it to secrets.webhook.secretKeyring.
# Rotation (add a key, switch to it, re-encrypt, remove the old key): docs/deployment/OPERATIONS-RUNBOOK.md.
#
# Losing the keyring makes every stored signing secret unreadable. Back it up apart from the database.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

command -v openssl >/dev/null || { echo "openssl not found (it ships with Git for Windows)" >&2; exit 1; }

new_key() {
  local key
  key="$(openssl rand -base64 32 | tr -d '\r\n')"
  # Standard Base64 of exactly 32 bytes is 43 characters plus one "=".
  [[ "$key" =~ ^[A-Za-z0-9+/]{43}=$ ]] || { echo "Key generation failed" >&2; exit 1; }
  printf '%s' "$key"
}

if [ "${1:-}" = "--key" ]; then
  new_key
  echo
  exit 0
fi

KEY_ID="${1:-v1}"
[[ "$KEY_ID" =~ ^[a-z0-9-]{1,32}$ ]] || { echo "Key id must match ^[a-z0-9-]{1,32}\$" >&2; exit 1; }
printf '{"activeKeyId":"%s","keys":{"%s":"%s"}}\n' "$KEY_ID" "$KEY_ID" "$(new_key)"
