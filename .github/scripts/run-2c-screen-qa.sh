#!/usr/bin/env bash
set -euo pipefail

resolution="${1:?Missing resolution}"
density="${2:?Missing density}"

mkdir -p qa-output/screenshots

adb shell wm size "$resolution"
adb shell wm density "$density"
adb shell settings put system font_scale 1.0

ANDROID_QA_OUTPUT_DIR=qa-output bash .github/scripts/run-android-instrumentation.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hebi.tripcanvas.qa.TripCanvas2CScreenshotTest

find qa-output -type f -print | sort > qa-output/files.txt
find qa-output/screenshots -type f -name '*.png' -print0 | sort -z | xargs -0 -r sha256sum > qa-output/screenshots-sha256.txt
count=$(find qa-output/screenshots -type f -name '*.png' | wc -l | tr -d ' ')

echo "Screenshot count: $count"
cat qa-output/files.txt

if [ "$count" -lt 16 ]; then
  echo "Missing real Android screen captures: expected at least 16, got $count."
  exit 1
fi
