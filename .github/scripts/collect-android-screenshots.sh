#!/usr/bin/env bash
set -euo pipefail

suite="${1:?Missing screenshot suite}"
evidence_dir="${2:?Missing evidence directory}"
case "$suite" in qa|qa3a) ;; *) exit 2 ;; esac

mkdir -p "$evidence_dir/screenshots"
remote_files=/sdcard/Android/data/com.hebi.tripcanvas/files
adb shell ls -la "$remote_files/$suite/" > "$evidence_dir/$suite-device-files.txt" 2>&1 || true

if adb exec-out tar -C "$remote_files" -cf - "$suite" \
    > "$evidence_dir/$suite-screenshots.tar" 2> "$evidence_dir/$suite-archive-errors.txt" \
    && tar -tf "$evidence_dir/$suite-screenshots.tar" > "$evidence_dir/$suite-archive-files.txt" 2>&1; then
  tar -xf "$evidence_dir/$suite-screenshots.tar" -C "$evidence_dir/screenshots/"
else
  adb pull "$remote_files/$suite/" "$evidence_dir/screenshots/" > "$evidence_dir/$suite-adb-pull.txt" 2>&1
fi
