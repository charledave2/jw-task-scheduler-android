#!/usr/bin/env bash
set -euo pipefail
trap 'mkdir -p ui-review; adb pull /sdcard/Pictures/jw-ui-review/. ui-review/ || true' EXIT
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
bash .github/scripts/verify-session-restart.sh
gradle --no-daemon :app:connectedDebugAndroidTest
