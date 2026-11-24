#!/usr/bin/env python3
"""Verify TripCanvas Korean store assets using the Python standard library only."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import struct
import sys

BASE = Path(__file__).resolve().parents[2] / 'assets' / 'play-store' / 'ko-KR'
EXPECTED = {
    'store-icon-512.png': ((512, 512), 6),
    'feature-graphic-1024x500.png': ((1024, 500), 2),
    '01-home.png': ((1350, 2400), 2),
    '02-workspace.png': ((1350, 2400), 2),
    '03-schedule.png': ((1350, 2400), 2),
    '04-map.png': ((1350, 2400), 2),
    '05-checklist.png': ((1350, 2400), 2),
    '06-expenses.png': ((1350, 2400), 2),
    '07-settlement.png': ((1350, 2400), 2),
    '08-bookings.png': ((1350, 2400), 2),
}


def inspect_png(p: Path):
    blob = p.read_bytes()
    if not blob.startswith(b'\x89PNG\r\n\x1a\n') or blob[12:16] != b'IHDR':
        raise ValueError(f'{p.name}: not a PNG')
    width, height, depth, color = struct.unpack('>IIBB', blob[16:26])
    if depth != 8:
        raise ValueError(f'{p.name}: 8-bit PNG required')
    return blob, (width, height), color


def main() -> int:
    if not BASE.is_dir():
        print(f'Assets folder missing: {BASE}', file=sys.stderr)
        return 1
    manifest = json.loads((BASE/'manifest.json').read_text(encoding='utf-8'))
    files = manifest['files']
    names = [item['name'] for item in files]
    if names != list(EXPECTED):
        raise ValueError(f'Order or asset list mismatch: {names}')
    for entry in files:
        name=entry['name']
        raw, size, color=inspect_png(BASE/name)
        spec_size, spec_color = EXPECTED[name]
        if size != spec_size or color != spec_color:
            raise ValueError(f'{name}: expected {spec_size}, color={spec_color} but got {size}, color={color}')
        if hashlib.sha256(raw).hexdigest() != entry['sha256']:
            raise ValueError(f'{name}: SHA-256 mismatch')
        if len(raw) != entry['size_bytes']:
            raise ValueError(f'{name}: size mismatch')
        if name == 'store-icon-512.png' and len(raw) > 1024*1024:
            raise ValueError('Store icon exceeds 1 MiB')
        print('OK:',name,size,'RGBA' if color == 6 else 'RGB')
    print('TripCanvas ko-KR: 10 store images validated.')
    return 0

if __name__ == '__main__':
    sys.exit(main())
