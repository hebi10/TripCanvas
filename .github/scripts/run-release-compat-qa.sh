#!/usr/bin/env bash
set -euo pipefail

expected_api="${1:?Android API level required}"
actual_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$actual_api" != "$expected_api" ]]; then
  echo "Expected Android API $expected_api but emulator reported $actual_api" >&2
  exit 1
fi

evidence_dir="qa-output/compat/api$expected_api"
mkdir -p "$evidence_dir"
printf '%s\n' "$actual_api" > "$evidence_dir/android-api.txt"

export ANDROID_QA_OUTPUT_DIR="$evidence_dir/instrumentation"
bash .github/scripts/run-android-instrumentation.sh

adb shell settings put system font_scale 1.6
adb shell am force-stop com.hebi.tripcanvas || true
adb shell am start -W -n com.hebi.tripcanvas/.MainActivity | tee "$evidence_dir/launch.txt"
sleep 3

app_pid="$(adb shell pidof com.hebi.tripcanvas | tr -d '\r')"
if [[ -z "$app_pid" ]]; then
  adb logcat -d -v threadtime > "$evidence_dir/startup-failure-logcat.txt" || true
  echo "TripCanvas process did not remain running after activity launch" >&2
  exit 1
fi

printf '%s\n' "$app_pid" > "$evidence_dir/app-process.txt"
adb exec-out screencap -p > "$evidence_dir/launch-font-160.png"
adb shell dumpsys activity activities > "$evidence_dir/launch-activity.txt"
adb shell settings get system font_scale > "$evidence_dir/font-scale.txt"
adb shell uiautomator dump /sdcard/tripcanvas-compat.xml > "$evidence_dir/hierarchy-dump.txt" 2>&1 || true
adb pull /sdcard/tripcanvas-compat.xml "$evidence_dir/hierarchy.xml" > "$evidence_dir/hierarchy-pull.txt" 2>&1 || true

test -s "$evidence_dir/launch-font-160.png"
echo "API $actual_api: Room migration instrumentation and 160% font activity launch passed"
