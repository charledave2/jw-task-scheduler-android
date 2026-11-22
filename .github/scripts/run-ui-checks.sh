#!/usr/bin/env bash
set -euo pipefail
trap 'mkdir -p ui-review; adb pull /sdcard/Android/data/com.quarry.congregationscheduler.test/files/ui-review/. ui-review/ || true' EXIT
gradle --no-daemon :app:connectedDebugAndroidTest
