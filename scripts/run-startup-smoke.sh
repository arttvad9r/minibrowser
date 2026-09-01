#!/usr/bin/env bash
set -euo pipefail

app_apk="$(find app/build/outputs/apk/debug -type f -name '*.apk' -print -quit)"
test_apk="$(find app/build/outputs/apk/androidTest/debug -type f -name '*.apk' -print -quit)"
test -n "$app_apk"
test -n "$test_apk"

adb wait-for-device

services_ready=0
for attempt in $(seq 1 30); do
  if adb shell cmd package list packages >/dev/null 2>&1 \
    && adb shell settings get global window_animation_scale >/dev/null 2>&1; then
    services_ready=1
    break
  fi
  echo "Android services not ready yet (attempt $attempt/30)"
  sleep 2
done
if (( services_ready == 0 )); then
  echo "Android package/settings services did not become ready"
  adb shell getprop sys.boot_completed || true
  adb shell dumpsys meminfo || true
  exit 1
fi

echo '--- guest memory before install ---'
adb shell free -m || adb shell cat /proc/meminfo | head -n 20 || true

install_apk() {
  local apk="$1"
  local installed=0
  for attempt in $(seq 1 3); do
    if adb install -r "$apk"; then
      installed=1
      break
    fi
    echo "APK install failed (attempt $attempt/3): $apk"
    adb wait-for-device || true
    sleep 3
  done
  if (( installed == 0 )); then
    echo "Failed to install APK after retries: $apk"
    return 1
  fi
}

install_apk "$app_apk"
install_apk "$test_apk"

echo '--- guest memory before instrumentation ---'
adb shell free -m || adb shell cat /proc/meminfo | head -n 20 || true

output="$(mktemp)"
trap 'rm -f "$output"' EXIT

# Clearing logcat is diagnostic-only and is not permitted by every legacy system image.
adb logcat -c || true
set +e
adb shell am instrument -w -r \
  -e class com.artt.minibrowser.MainActivityStartupTest \
  com.artt.minibrowser.test/androidx.test.runner.AndroidJUnitRunner \
  | tr -d '\r' | tee "$output"
instrument_status=${PIPESTATUS[0]}
set -e

failed=0
if (( instrument_status != 0 )); then
  echo "Instrumentation shell command exited with status $instrument_status"
  failed=1
fi
if grep -Fq 'shortMsg=Process crashed.' "$output"; then
  echo "Instrumentation process crashed"
  failed=1
fi
if grep -Eq '^INSTRUMENTATION_STATUS_CODE: -[12]$' "$output"; then
  echo "Instrumentation reported a failing/error status code"
  failed=1
fi
if ! grep -Fqx 'OK (2 tests)' "$output"; then
  echo "Instrumentation did not complete both startup tests"
  failed=1
fi

if (( failed != 0 )); then
  echo '--- guest memory after instrumentation failure ---'
  adb shell free -m || true
  echo '--- crash buffer ---'
  adb logcat -b crash -d -v threadtime || true
  echo '--- recent app/test/system-memory logcat ---'
  adb logcat -d -v threadtime \
    | grep -E 'com\.artt\.minibrowser|AndroidRuntime|DEBUG|libc|Gecko|FATAL|crash|tombstone|lowmemorykiller|lmkd' \
    | tail -n 1200 || true
  exit 1
fi
