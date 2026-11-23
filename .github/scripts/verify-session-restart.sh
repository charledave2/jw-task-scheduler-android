#!/usr/bin/env bash
set -euo pipefail
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
TEST_RUNNER=$(adb shell pm list instrumentation | tr -d '\r' | sed -n '/(target=com.quarry.congregationscheduler.test)/s/^instrumentation:\([^ ]*\).*/\1/p' | head -n 1)
test -n "$TEST_RUNNER"
for SESSION_PHASE in seed verify; do
  SESSION_RESULT=$(mktemp)
  adb shell am instrument -w -r -e class com.quarry.congregationscheduler.SessionProcessRestartTest -e session_phase "$SESSION_PHASE" "$TEST_RUNNER" | tee "$SESSION_RESULT"
  grep -Eq 'OK \(1 test\)' "$SESSION_RESULT"
  adb shell am force-stop com.quarry.congregationscheduler.test
done
