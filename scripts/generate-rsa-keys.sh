#!/usr/bin/env bash
# Generate a 2048-bit RSA key pair for SwiftDrop IAM RS256 signing.
#
# Usage: scripts/generate-rsa-keys.sh [output-dir]
#
# Writes PKCS#8 private.pem and X.509 public.pem into the output directory
# (default: ./secrets, which is gitignored) and prints .env- and kubectl-ready
# values. Existing keys are never overwritten.

set -euo pipefail

output_dir="${1:-secrets}"
private_key="$output_dir/private.pem"
public_key="$output_dir/public.pem"

if [[ -e "$private_key" || -e "$public_key" ]]; then
  echo "Refusing to overwrite existing key material in $output_dir" >&2
  echo "Move or delete the files first, then rerun." >&2
  exit 1
fi

mkdir -p "$output_dir"
chmod 700 "$output_dir" 2>/dev/null || true

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$private_key" 2>/dev/null
openssl pkey -in "$private_key" -pubout -out "$public_key" 2>/dev/null
chmod 600 "$private_key" 2>/dev/null || true
chmod 644 "$public_key" 2>/dev/null || true

if ! openssl rsa -in "$private_key" -check -noout >/dev/null 2>&1; then
  echo "Generated private key failed validation" >&2
  exit 1
fi
if ! openssl pkey -in "$private_key" -pubout -noout >/dev/null 2>&1; then
  echo "Generated key pair could not be read" >&2
  exit 1
fi

single_line() {
  awk 'NF {printf "%s\\n", $0}' "$1"
}

echo "Wrote PKCS#8 private key: $private_key"
echo "Wrote X.509 public key:  $public_key"
echo
echo "Add these single-line values to .env (keep the single quotes):"
printf "JWT_PRIVATE_KEY='%s'\n" "$(single_line "$private_key")"
printf "JWT_PUBLIC_KEY='%s'\n" "$(single_line "$public_key")"
echo
echo "Create the AKS secret (keeps the HS256 rollback secret key as well):"
printf 'kubectl -n swiftdrop-apps create secret generic swiftdrop-jwt-credentials \\\n'
printf '  --from-file=private-key=%s \\\n' "$private_key"
printf '  --from-file=public-key=%s \\\n' "$public_key"
printf '  --from-literal=secret="$(openssl rand -base64 32)" \\\n'
printf '  --dry-run=client -o yaml | kubectl apply -f -\n'
