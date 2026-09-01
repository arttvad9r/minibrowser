#!/usr/bin/env bash
set -euo pipefail

output="$(mktemp)"
trap 'rm -f "$output"' EXIT

adb logcat -c
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
  echo '--- crash buffer ---'
  adb logcat -b crash -d -v threadtime || true
  echo '--- recent app/test logcat ---'
  adb logcat -d -v threadtime \
    | grep -E 'com\.artt\.minibrowser|AndroidRuntime|DEBUG|libc|Gecko|FATAL|crash|tombstone' \
    | tail -n 1200 || true
  exit 1
fi
