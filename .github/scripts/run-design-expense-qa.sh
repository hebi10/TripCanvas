#!/usr/bin/env bash
set -euo pipefail

resolution="${1:?Screen resolution required}"
density="${2:?Screen density required}"

bash .github/scripts/run-3b-screen-qa.sh "$resolution" "$density"

set +e
adb shell am instrument -w \
  -e class com.hebi.tripcanvas.qa.TripCanvasReceiptOcrTest \
  com.hebi.tripcanvas.test/androidx.test.runner.AndroidJUnitRunner \
  | tee qa-output/ocr-instrumentation.txt
status=${PIPESTATUS[0]}
set -e

bash .github/scripts/collect-android-screenshots.sh qa3b qa-output
find qa-output/screenshots -type f -name '*.png' -print0 | sort -z | xargs -0 -r sha256sum > qa-output/screenshots-sha256.txt
find qa-output -type f -print | sort > qa-output/files.txt
if [ "$status" -ne 0 ]; then exit "$status"; fi
grep -q 'OK (1 test)' qa-output/ocr-instrumentation.txt
for name in f06-ocr-before-selection f07-ocr-candidate-dialog f08-ocr-cancel-preserved \
  f09-ocr-applied-unsaved f10-ocr-expense-saved; do
  test -s "qa-output/screenshots/qa3b/$name.png" || { echo "Missing OCR evidence: $name" >&2; exit 1; }
done
