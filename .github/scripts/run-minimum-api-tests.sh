#!/usr/bin/env bash
set -euo pipefail
# Avoid AGP's streamed split installer on the API 24 emulator. Build the same
# instrumentation APKs, then use the older device's non-streamed install path.
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
TEST_RUNNER=$(adb shell pm list instrumentation | tr -d '\r' | sed -n '/(target=com.quarry.congregationscheduler.test)/s/^instrumentation:\([^ ]*\).*/\1/p' | head -n 1)
test -n "$TEST_RUNNER"
RESULT=$(mktemp)
adb shell am instrument -w -r -e class com.quarry.congregationscheduler.StartupCompatibilityTest,com.quarry.congregationscheduler.LocalIdentityTest,com.quarry.congregationscheduler.SessionPersistenceTest "$TEST_RUNNER" | tee "$RESULT"
# `am instrument` can return zero even if a test fails.
grep -Eq 'OK \(5 tests\)' "$RESULT"

bash .github/scripts/verify-session-restart.sh
