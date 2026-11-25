#!/usr/bin/env bash
set -euo pipefail

resolution="${1:?Screen resolution required}"
density="${2:?Screen density required}"
mkdir -p qa-output/screenshots
adb shell wm size "$resolution"
adb shell wm density "$density"
adb shell settings put system font_scale 1.0

ANDROID_QA_OUTPUT_DIR=qa-output bash .github/scripts/run-android-instrumentation.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hebi.tripcanvas.qa.TripCanvasFirstLaunchTest

for name in 00-welcome-light 00a-welcome-start-action 01-first-home-empty \
  02-create-trip-light 02a-create-trip-date-picker 02b-create-trip-local-settings \
  02c-create-trip-bottom 02d-create-trip-required-title 03-new-trip-workspace \
  04-first-created-trip-home 05-reopened-home; do
  if [[ ! -s "qa-output/screenshots/qa/$name.png" ]]; then
    echo "Missing first-launch evidence: $name" >&2
    exit 1
  fi
done
find qa-output/screenshots -type f -name '*.png' -print0 | sort -z | xargs -0 -r sha256sum > qa-output/screenshots-sha256.txt
find qa-output -type f -print | sort > qa-output/files.txt
