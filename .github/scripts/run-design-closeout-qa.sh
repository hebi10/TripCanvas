#!/usr/bin/env bash
set -euo pipefail
resolution="${1:?Screen resolution required}"
density="${2:?Screen density required}"
mkdir -p qa-output/screenshots
adb shell wm size "$resolution"
adb shell wm density "$density"
adb shell settings put system font_scale 1.0
class=com.hebi.tripcanvas.qa.TripCanvas2CScreenshotTest
ANDROID_QA_OUTPUT_DIR=qa-output bash .github/scripts/run-android-instrumentation.sh \
  "-Pandroid.testInstrumentationRunnerArguments.class=$class#captureSettingsAndBackupSafety,$class#captureVerifiedAttachmentRestoreCancellation,$class#captureJournalPhotoAndZipExport"

for name in s01-settings-light s02-settings-backup-formats s03-settings-subscription \
  s04-settings-version s05-backup-overview s06-backup-password-empty s07-backup-password-short \
  s08-backup-password-mismatch s09-backup-media-format s10-backup-plain-format s11-backup-restore-options \
  s12-backup-checkpoint s13-backup-cleanup-confirm s14-settings-dark-font160 s15-backup-dark-font160 \
  s16-checkpoint-dark-font160 s17-backup-password-import s18-backup-attachment-verified \
  s19-backup-attachment-cancelled s20-journal-photo-preview s21-journal-zip-exported s22-report-photo-preview; do
  test -s "qa-output/screenshots/qa/$name.png" || { echo "Missing closeout evidence: $name" >&2; exit 1; }
done
find qa-output/screenshots -type f -name '*.png' -print0 | sort -z | xargs -0 -r sha256sum > qa-output/screenshots-sha256.txt
find qa-output -type f -print | sort > qa-output/files.txt
