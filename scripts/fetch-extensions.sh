#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ASSETS=app/src/main/assets/extensions
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$ASSETS/ublock" "$ASSETS/vot"

# uBlock Origin: последний релиз firefox-signed xpi
UBO_TAG=$(curl -fsSL https://api.github.com/repos/gorhill/uBlock/releases/latest | grep -oP '"tag_name":\s*"\K[^"]+')
curl -fsSL -o "$WORK/ubo.xpi" \
  "https://github.com/gorhill/uBlock/releases/download/${UBO_TAG}/uBlock0_${UBO_TAG}.firefox.signed.xpi"
rm -rf "$ASSETS/ublock" && mkdir -p "$ASSETS/ublock"
unzip -q "$WORK/ubo.xpi" -d "$ASSETS/ublock"

# VOT: последний релиз, ассет vot-extension-firefox.xpi
VOT_URL=$(curl -fsSL https://api.github.com/repos/ilyhalight/voice-over-translation/releases/latest \
  | grep -oP '"browser_download_url":\s*"\K[^"]*vot-extension-firefox\.xpi')
curl -fsSL -o "$WORK/vot.xpi" "$VOT_URL"
rm -rf "$ASSETS/vot" && mkdir -p "$ASSETS/vot"
unzip -q "$WORK/vot.xpi" -d "$ASSETS/vot"

echo "--- uBO id:"; grep -oP '"id"\s*:\s*"\K[^"]+' "$ASSETS/ublock/manifest.json" || echo "(нет explicit id)"
echo "--- VOT id:"; grep -oP '"id"\s*:\s*"\K[^"]+' "$ASSETS/vot/manifest.json" || echo "(нет explicit id)"
