#!/usr/bin/env bash
set -euo pipefail

OUT_FILE="${1:-fdroid-repo-keystore.p12}"
ALIAS="${2:-wupp-fdroid-repo}"
UPLOAD_FLAG="${3:-}"

if [ -e "$OUT_FILE" ]; then
  echo "Error: $OUT_FILE already exists. Back it up first so you don't overwrite an existing repo key."
  exit 1
fi

PASS=$(openssl rand -base64 24 | tr '+/=' 'AB9')

keytool -genkeypair \
  -keystore "$OUT_FILE" \
  -storetype PKCS12 \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$PASS" \
  -keypass "$PASS" \
  -dname "CN=Wupp F-Droid Repo, O=Wupp, C=US" >/dev/null

FP=$(keytool -list -v -keystore "$OUT_FILE" -storepass "$PASS" -alias "$ALIAS" | awk -F'SHA256: ' '/SHA256:/{gsub(/:/,"",$2); print $2; exit}')
B64=$(base64 -w 0 "$OUT_FILE" 2>/dev/null || base64 "$OUT_FILE" | tr -d '\n')

echo ""
echo "Generated $OUT_FILE (BACK THIS FILE UP SAFELY, DO NOT COMMIT TO GIT)"
echo "Repo SHA-256 Fingerprint: ${FP}"

if [ "$UPLOAD_FLAG" = "--upload" ]; then
  printf '%s' "$B64" | gh secret set FDROID_REPO_KEYSTORE_BASE64
  printf '%s' "$PASS" | gh secret set FDROID_REPO_KEYSTORE_PASS
  printf '%s' "$PASS" | gh secret set FDROID_REPO_KEY_PASS
  printf '%s' "$ALIAS" | gh secret set FDROID_REPO_KEY_ALIAS
  echo "Uploaded FDROID_REPO_KEYSTORE_* secrets to GitHub via gh."
else
  echo "Re-run with '$0 $OUT_FILE $ALIAS --upload' to push secrets via gh."
fi
