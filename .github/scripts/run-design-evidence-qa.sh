#!/usr/bin/env bash
set -euo pipefail

suite="${1:?Design QA suite required}"
resolution="${2:?Screen resolution required}"
density="${3:?Screen density required}"
case "$suite" in
  .github/scripts/run-2c-screen-qa.sh|.github/scripts/run-3a-map-qa.sh|.github/scripts/run-3b-screen-qa.sh) ;;
  *) echo "Unsupported design QA suite: $suite" >&2; exit 2 ;;
esac
mkdir -p qa-output

verify_display() {
  local phase="$1"
  adb shell wm size > "qa-output/design-${phase}-size.txt" || return 1
  adb shell wm density > "qa-output/design-${phase}-density.txt" || return 1
  python3 - "$phase" "$resolution" "$density" <<'PY'
import re
import struct
import sys
from pathlib import Path

phase, expected_size, expected_density = sys.argv[1:]
root = Path('qa-output')

def effective(kind, pattern):
    text = (root / f'design-{phase}-{kind}.txt').read_text()
    matches = dict(re.findall(rf'(?m)^(Physical|Override) {kind}: ({pattern})\s*$', text))
    return matches.get('Override', matches.get('Physical', 'unknown'))

size = effective('size', r'\d+x\d+')
density = effective('density', r'\d+')
report = [f'phase={phase}', f'expected_size={expected_size}', f'actual_size={size}',
          f'expected_density={expected_density}', f'actual_density={density}']
errors = []
if size != expected_size or density != expected_density:
    errors.append(f'Display mismatch: {size}/{density}, expected {expected_size}/{expected_density}')
if phase == 'after':
    images = sorted((root / 'screenshots').rglob('*.png'))
    if not images:
        errors.append('No Android screenshot PNGs were collected')
    final = root / 'final-screen.png'
    if final.is_file():
        images.append(final)
    report.append(f'png_count={len(images)}')
    for path in images:
        with path.open('rb') as stream:
            header = stream.read(24)
        if len(header) != 24 or header[:8] != b'\x89PNG\r\n\x1a\n' or header[12:16] != b'IHDR':
            errors.append(f'Invalid PNG header: {path}')
            continue
        width, height = struct.unpack('>II', header[16:24])
        actual = f'{width}x{height}'
        report.append(f'{path.relative_to(root)}\t{actual}')
        if actual != expected_size:
            errors.append(f'PNG size mismatch: {path}: {actual} != {expected_size}')
report.extend(f'ERROR: {error}' for error in errors)
(root / f'design-{phase}-display-report.txt').write_text('\n'.join(report) + '\n')
print('\n'.join(report))
raise SystemExit(1 if errors else 0)
PY
}

adb shell wm size "$resolution"
adb shell wm density "$density"
verify_display before

suite_status=0
bash "$suite" "$resolution" "$density" || suite_status=$?
display_status=0
verify_display after || display_status=$?

if (( suite_status != 0 )); then exit "$suite_status"; fi
exit "$display_status"
