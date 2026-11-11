#!/usr/bin/env python3
"""Validate or manually submit only TripCanvas ko-KR Google Play listing images.

Never uploads an Android bundle, modifies release tracks or edits other locales.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

PACKAGE = 'com.hebi.tripcanvas'
LOCALE = 'ko-KR'
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'assets' / 'play-store' / LOCALE
BASE = f'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}'
UPLOAD = f'https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}'
ASSET_TYPES = (
    ('icon', ('store-icon-512.png',)),
    ('featureGraphic', ('feature-graphic-1024x500.png',)),
    ('phoneScreenshots', (
        '01-home.png', '02-workspace.png', '03-schedule.png', '04-map.png',
        '05-checklist.png', '06-expenses.png', '07-settlement.png', '08-bookings.png',
    )),
)
SPECS = {
    'store-icon-512.png': (512, 512, 6),
    'feature-graphic-1024x500.png': (1024, 500, 2),
    **{name: (1350, 2400, 2) for name in ASSET_TYPES[2][1]},
}


class ListingError(RuntimeError):
    pass


def validate_assets(directory: Path = ASSETS) -> dict[str, dict]:
    manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('package') != PACKAGE or manifest.get('store_locale') != LOCALE:
        raise ListingError('Incorrect app package or locale in asset manifest')
    entries = manifest.get('files', [])
    expected = [name for _, group in ASSET_TYPES for name in group]
    if [entry.get('name') for entry in entries] != expected or len(entries) != 10:
        raise ListingError('Unexpected listing image names or order')
    extra = set(p.name for p in directory.glob('[0-9][0-9]-*.png')) - set(expected)
    if extra:
        raise ListingError('Unexpected additional screenshot files')
    result = {}
    for entry in entries:
        name = entry['name']
        data = (directory / name).read_bytes()
        if len(data) < 64 or len(data) > (1024 * 1024 if name == 'store-icon-512.png' else 15 * 1024 * 1024):
            raise ListingError(f'Image file size invalid: {name}')
        if data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
            raise ListingError(f'Not a valid PNG signature: {name}')
        width, height, bits, color = struct.unpack('>IIBB', data[16:26])
        if (width, height, bits, color) != (*SPECS[name][:2], 8, SPECS[name][2]):
            raise ListingError(f'Unexpected dimensions, bit depth or channel format: {name}')
        digest = hashlib.sha256(data).hexdigest()
        if (entry.get('sha256') != digest or entry.get('size_bytes') != len(data)):
            raise ListingError(f'Asset manifest hash/size mismatch: {name}')
        result[name] = {'sha256': digest, 'data': data}
    return result


class PlayClient:
    def __init__(self, token: str, opener=urllib.request.urlopen):
        if not token:
            raise ListingError('Google Play access token was not supplied')
        self.token = token
        self.opener = opener

    def request(self, method: str, path: str, payload=None, *, media=False, stage='request') -> dict:
        if not path.startswith('/edits') or '\r' in path or '\n' in path:
            raise ListingError('Refused unapproved Play API path')
        endpoint = (UPLOAD if media else BASE) + path
        raw = None
        if isinstance(payload, dict):
            raw = json.dumps(payload, separators=(',', ':')).encode('utf-8')
        elif isinstance(payload, bytes):
            raw = payload
        headers = {
            'Authorization': f'Bearer {self.token}',
            'Accept': 'application/json',
            'Content-Type': 'image/png' if media else 'application/json',
        }
        req = urllib.request.Request(endpoint, data=raw, headers=headers, method=method)
        try:
            with self.opener(req, timeout=75) as response:
                content = response.read()
                return json.loads(content) if content else {}
        except urllib.error.HTTPError as error:
            # Never report the original server body: it may contain account or edit metadata.
            raise ListingError(f'Google Play {stage} failed (HTTP {error.code})') from None
        except (urllib.error.URLError, TimeoutError, ValueError):
            raise ListingError(f'Google Play {stage} could not be completed') from None


def start_edit(client: PlayClient) -> str:
    edit_id = str(client.request('POST', '/edits', {}, stage='edit_create').get('id', ''))
    if not re.fullmatch('[A-Za-z0-9_-]{1,128}', edit_id):
        raise ListingError('Play API returned an invalid edit identifier')
    return edit_id


def path_for(edit_id: str, image_type: str) -> str:
    if image_type not in {kind for kind, _ in ASSET_TYPES}:
        raise ListingError('Unsupported listing image type')
    return f'/edits/{edit_id}/listings/{LOCALE}/{image_type}'


def current_digests(client: PlayClient, edit_id: str, image_type: str) -> list[str]:
    response = client.request('GET', path_for(edit_id, image_type), stage='image_readback')
    return [str(x.get('sha256', '')).lower() for x in response.get('images', [])]


def require_snapshot(client: PlayClient, edit_id: str, assets: dict, stage: str) -> None:
    for image_type, names in ASSET_TYPES:
        actual = current_digests(client, edit_id, image_type)
        expected = [assets[name]['sha256'] for name in names]
        if actual != expected:
            raise ListingError(f'Play {stage} does not match local images: {image_type}')


def submit(client: PlayClient, assets: dict) -> str:
    edit_id = start_edit(client)
    committed = False
    try:
        # Guard against committing into an absent localization.
        listing = client.request('GET', f'/edits/{edit_id}/listings/{LOCALE}', stage='locale_check')
        if listing.get('language') != LOCALE:
            raise ListingError('The Korean store listing is not registered; refusing upload')
        for image_type, names in ASSET_TYPES:
            endpoint = path_for(edit_id, image_type)
            client.request('DELETE', endpoint, stage='images_replace')
            for name in names:
                response = client.request('POST', endpoint + '?uploadType=media', assets[name]['data'],
                                          media=True, stage='image_upload')
                if not response.get('image', {}).get('id'):
                    raise ListingError('Play API upload did not return an image id')
        require_snapshot(client, edit_id, assets, 'uncommitted_edit')
        client.request('POST', f'/edits/{edit_id}:validate', {}, stage='edit_validate')
        # Do not cancel an existing Google review or implicitly submit changes for review.
        client.request('POST', f'/edits/{edit_id}:commit?changesNotSentForReview=true&changesInReviewBehavior=ERROR_IF_IN_REVIEW',
                       {}, stage='edit_commit')
        committed = True
    finally:
        if not committed:
            try:
                client.request('DELETE', f'/edits/{edit_id}', stage='discard_edit')
            except ListingError:
                pass
    readback_id = start_edit(client)
    try:
        require_snapshot(client, readback_id, assets, 'fresh_readback')
    finally:
        try:
            client.request('DELETE', f'/edits/{readback_id}', stage='discard_readback')
        except ListingError:
            pass
    return 'Google Play edit committed and ko-KR images rechecked; review, publishing and installability remain unverified.'


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Change Play Korean images (requires manual workflow dispatch)')
    args = parser.parse_args()
    try:
        assets = validate_assets()
        if args.apply:
            if os.environ.get('GITHUB_REPOSITORY') != 'hebi10/TripCanvas' or os.environ.get('GITHUB_REF') != 'refs/heads/main':
                raise ListingError('Image upload requires the trusted repository main branch')
            event_name = os.environ.get('GITHUB_EVENT_NAME')
            if event_name == 'push':
                subject = subprocess.run(['git', 'show', '-s', '--format=%s', 'HEAD'],
                                         capture_output=True, text=True, check=False)
                if (subject.returncode or subject.stdout.strip() !=
                    'store: TripCanvas STORE-03 Korean listing [tripcanvas-store03-listing-once]'):
                    raise ListingError('Automatic upload is restricted to the authorized STORE-03 merge')
            elif event_name != 'workflow_dispatch':
                raise ListingError('Image upload requires manual dispatch or the authorized STORE-03 merge')
            print(submit(PlayClient(os.environ.get('PLAY_ACCESS_TOKEN', '')), assets))
        else:
            print('TripCanvas ko-KR: 10 image assets verified; no Play API changes.')
        return 0
    except (ListingError, FileNotFoundError, OSError, ValueError, KeyError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
