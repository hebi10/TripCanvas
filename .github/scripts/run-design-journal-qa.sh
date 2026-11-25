#!/usr/bin/env bash
set -euo pipefail

resolution="${1:?Screen resolution required}"
density="${2:?Screen density required}"
mkdir -p qa-output/screenshots
adb shell wm size "$resolution"
adb shell wm density "$density"
adb shell settings put system font_scale 1.0

ANDROID_QA_OUTPUT_DIR=qa-output bash .github/scripts/run-android-instrumentation.sh \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.hebi.tripcanvas.qa.TripCanvas2CScreenshotTest#captureLiveMemoriesAndReportFromRealAndroidUi'

for name in 24-p3-live-before-checkin 25-p3-live-after-checkin 26-p3-memories-visit \
  27-p3-report-visited j01-live-note-light j02-live-expense-action j03-memory-editor-linked \
  j04-memory-saved-light j05-memory-no-photo-export j06-report-expense-light \
  j07-report-font160 j08-report-expense-font160; do
  test -s "qa-output/screenshots/qa/$name.png" || { echo "Missing journal evidence: $name" >&2; exit 1; }
done
find qa-output/screenshots -type f -name '*.png' -print0 | sort -z | xargs -0 -r sha256sum > qa-output/screenshots-sha256.txt
find qa-output -type f -print | sort > qa-output/files.txt
