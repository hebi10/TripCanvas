#!/usr/bin/env bash
set -euo pipefail
resolution="${1:?Android screen resolution required}"
density="${2:?Screen density required}"

mkdir -p qa-output/screenshots
adb shell wm size "$resolution"
adb shell wm density "$density"
adb shell settings put system font_scale 1.0
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

set +e
adb shell am instrument -w \
  -e class com.hebi.tripcanvas.qa.TripCanvas3BScreenTest \
  com.hebi.tripcanvas.test/androidx.test.runner.AndroidJUnitRunner \
  | tee qa-output/instrumentation.txt
status=${PIPESTATUS[0]}
set -e

bash .github/scripts/collect-android-screenshots.sh qa3b qa-output || true
count=$(find qa-output/screenshots -type f -name '*.png' | wc -l | tr -d ' ')
if [ "$count" -lt 13 ]; then
  adb pull /sdcard/Android/data/com.hebi.tripcanvas/files/qa3b/ qa-output/screenshots/ \
    > qa-output/qa3b-adb-pull-fallback.txt 2>&1 || true
fi
find qa-output/screenshots -type f -name '*.png' -print0 | sort -z | xargs -0 -r sha256sum > qa-output/screenshots-sha256.txt
find qa-output -type f | sort > qa-output/files.txt
count=$(find qa-output/screenshots -type f -name '*.png' | wc -l | tr -d ' ')
echo "Captured actual Android PNGs: $count"
cat qa-output/files.txt

if [ "$status" -ne 0 ]; then exit "$status"; fi
if ! grep -q 'OK (1 test)' qa-output/instrumentation.txt; then exit 1; fi
if [ "$count" -lt 13 ]; then echo "Missing 3-B Android screen captures"; exit 1; fi
