#!/usr/bin/env bash
set -euo pipefail
# Report names only. Never print or retrieve signing secret values.
missing=false
for name in THT_SIGN_KEYSTORE_BASE64 THT_SIGN_STORE_PASSWORD THT_SIGN_KEY_ALIAS THT_SIGN_KEY_PASSWORD THT_SIGN_CERT_SHA256; do
  if [[ -z "${!name:-}" ]]; then
    echo "Thiếu $name trong môi trường ký của repo PIP-Viet." >&2
    missing=true
  fi
done
[[ "$missing" == false ]] || exit 1
expected="$(printf '%s' "$THT_SIGN_CERT_SHA256" | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"
[[ "$expected" =~ ^[0-9A-F]{64}$ ]] || { echo 'THT_SIGN_CERT_SHA256 phải là SHA-256 chứng thư gồm 64 ký tự hex.' >&2; exit 1; }
echo 'Đủ tên biến THT_SIGN_*; chứng thư thực tế sẽ được đối chiếu sau khi ký APK.'
