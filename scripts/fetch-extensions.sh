#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

LOCK=extensions.lock
ASSETS=app/src/main/assets/extensions
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

readarray -t LOCK_VALUES < <(python3 - "$LOCK" <<'PY'
import json, sys
lock = json.load(open(sys.argv[1]))
for name in ("ublock", "vot"):
    item = lock[name]
    print("\t".join((item["version"], item["id"], item["url"], item["sha256"])))
PY
)

fetch_and_verify() {
    local name="$1" version="$2" extension_id="$3" url="$4" expected_sha="$5"
    local archive="$WORK/$name.xpi" extracted="$WORK/$name"

    curl -fsSL --retry 2 -o "$archive" "$url"
    actual_sha=$(sha256sum "$archive" | cut -d' ' -f1)
    [[ "$actual_sha" == "$expected_sha" ]] || {
        echo "$name checksum mismatch: expected $expected_sha, got $actual_sha" >&2
        exit 1
    }
    mkdir -p "$extracted"
    unzip -q -t "$archive"
    unzip -q "$archive" -d "$extracted"
    python3 - "$extracted/manifest.json" "$version" "$extension_id" <<'PY'
import json, sys
manifest = json.load(open(sys.argv[1]))
expected_version, expected_id = sys.argv[2:]
actual_id = manifest.get("browser_specific_settings", {}).get("gecko", {}).get("id")
if actual_id is None:
    actual_id = manifest.get("applications", {}).get("gecko", {}).get("id")
if manifest.get("version") != expected_version or actual_id != expected_id:
    raise SystemExit(f"manifest mismatch: version={manifest.get('version')} id={actual_id}")
PY
    rm -rf "$ASSETS/$name.new"
    mkdir -p "$ASSETS/$name.new"
    cp -a "$extracted/." "$ASSETS/$name.new/"
    rm -rf "$ASSETS/$name"
    mv "$ASSETS/$name.new" "$ASSETS/$name"
}

IFS=$'\t' read -r UBO_VERSION UBO_ID UBO_URL UBO_SHA <<<"${LOCK_VALUES[0]}"
IFS=$'\t' read -r VOT_VERSION VOT_ID VOT_URL VOT_SHA <<<"${LOCK_VALUES[1]}"
fetch_and_verify ublock "$UBO_VERSION" "$UBO_ID" "$UBO_URL" "$UBO_SHA"
fetch_and_verify vot "$VOT_VERSION" "$VOT_ID" "$VOT_URL" "$VOT_SHA"

echo "Verified uBlock $UBO_VERSION ($UBO_ID)"
echo "Verified VOT $VOT_VERSION ($VOT_ID)"
