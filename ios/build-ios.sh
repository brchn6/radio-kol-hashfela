#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "xcodebuild was not found. iOS builds require macOS with Xcode installed." >&2
  exit 1
fi

xcodebuild \
  -project "$PROJECT_DIR/RadioKolHashfela.xcodeproj" \
  -scheme RadioKolHashfela \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO \
  build
