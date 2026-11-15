#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGETS="${1:-android/arm64,android/arm,android/amd64}"

export PATH="$PATH:$(go env GOPATH)/bin"
if ! command -v gomobile >/dev/null 2>&1; then
  go install golang.org/x/mobile/cmd/gomobile@latest
  go install golang.org/x/mobile/cmd/gobind@latest
fi

gomobile init
mkdir -p "$ROOT_DIR/app/libs"
cd "$ROOT_DIR/whatsbridge"
gomobile bind -javapkg=com.wa.bridge -ldflags="-s -w" -target="$TARGETS" -androidapi 26 -o "$ROOT_DIR/app/libs/whatsbridge.aar" .
echo "Built $ROOT_DIR/app/libs/whatsbridge.aar"
