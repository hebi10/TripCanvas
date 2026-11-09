#!/usr/bin/env bash
set -euo pipefail

evidence_dir="${ANDROID_QA_OUTPUT_DIR:-qa-output/instrumentation}"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$evidence_dir/screenshots"

collect_evidence() {
  local test_status=$?
  trap - EXIT
  set +e
  bash "$script_dir/collect-android-screenshots.sh" qa "$evidence_dir"
  bash "$script_dir/collect-android-screenshots.sh" qa3a "$evidence_dir"
  bash "$script_dir/collect-android-screenshots.sh" qa3b "$evidence_dir"
  adb exec-out run-as com.hebi.tripcanvas tar -cf - databases files/datastore > "$evidence_dir/app-data.tar" 2> "$evidence_dir/app-data-errors.txt"
  adb exec-out screencap -p > "$evidence_dir/final-screen.png" 2> "$evidence_dir/final-screen-errors.txt"
  adb shell uiautomator dump /sdcard/tripcanvas-final-hierarchy.xml > "$evidence_dir/final-hierarchy-dump.txt" 2>&1
  adb pull /sdcard/tripcanvas-final-hierarchy.xml "$evidence_dir/final-hierarchy.xml" > "$evidence_dir/final-hierarchy-pull.txt" 2>&1
  adb logcat -d -v threadtime > "$evidence_dir/logcat.txt" 2>&1
  adb shell dumpsys activity activities > "$evidence_dir/activity.txt" 2>&1
  adb shell dumpsys window windows > "$evidence_dir/window.txt" 2>&1
  adb shell wm size > "$evidence_dir/display-size.txt" 2>&1
  adb shell wm density > "$evidence_dir/display-density.txt" 2>&1
  adb shell settings get system font_scale > "$evidence_dir/font-scale.txt" 2>&1
  if [[ -d app/build/outputs/androidTest-results ]]; then
    cp -a app/build/outputs/androidTest-results "$evidence_dir/"
  fi
  if [[ -d app/build/reports/androidTests ]]; then
    cp -a app/build/reports/androidTests "$evidence_dir/"
  fi
  exit "$test_status"
}

trap collect_evidence EXIT
set +e
./gradlew :app:connectedDebugAndroidTest --stacktrace \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "$@" 2>&1 | tee "$evidence_dir/gradle-connected.txt"
test_status=${PIPESTATUS[0]}
set -e
exit "$test_status"
