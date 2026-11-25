#!/usr/bin/env bash
set -euo pipefail

resolution="${1:?Screen resolution required}"
density="${2:?Screen density required}"
mkdir -p qa-output/screenshots
adb shell wm size "$resolution"
adb shell wm density "$density"
adb shell settings put system font_scale 1.0

ANDROID_QA_OUTPUT_DIR=qa-output bash .github/scripts/run-android-instrumentation.sh \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.hebi.tripcanvas.qa.TripCanvas2CScreenshotTest#capturePlaceManagementAndSelection'

for name in p01-place-list-light p02-place-create-light p03-place-search-light \
  p04-place-delete-confirm-light p05-place-detail-light p06-place-edit-light \
  p07-schedule-form-dark-font160 p08-place-select-dark-font160 \
  p09-schedule-place-linked-dark-font160 p10-schedule-saved-dark-font160 \
  p02a-place-coordinates-light p06a-schedule-time-fields-light \
  p07a-schedule-time-fields-dark-font160 p07b-place-coordinates-dark-font160; do
  test -s "qa-output/screenshots/qa/$name.png" || { echo "Missing place evidence: $name" >&2; exit 1; }
done
find qa-output/screenshots -type f -name '*.png' -print0 | sort -z | xargs -0 -r sha256sum > qa-output/screenshots-sha256.txt
find qa-output -type f -print | sort > qa-output/files.txt
