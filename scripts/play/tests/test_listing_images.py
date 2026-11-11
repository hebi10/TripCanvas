"""Offline Play listing update guards; never contacts Google Play."""
import hashlib
import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

MOD = Path(__file__).resolve().parents[1] / 'listing_images.py'
spec = importlib.util.spec_from_file_location('tripcanvas_listing_images', MOD)
listing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(listing)


class FakePlay:
    def __init__(self, remote=None, refuse_commit=False):
        self.remote = remote or {t: [] for t, _ in listing.ASSET_TYPES}
        self.working = {}
        self.next_id = 0
        self.calls = []
        self.refuse_commit = refuse_commit

    def request(self, method, path, payload=None, *, media=False, stage='request'):
        self.calls.append((method, path, media))
        if method == 'POST' and path == '/edits':
            self.next_id += 1
            edit = str(self.next_id)
            self.working[edit] = {t: list(self.remote[t]) for t, _ in listing.ASSET_TYPES}
            return {'id': edit}
        edit = path.split('/')[2].split(':')[0]
        if method == 'DELETE' and path == f'/edits/{edit}':
            self.working.pop(edit, None)
            return {}
        if method == 'GET' and path == f'/edits/{edit}/listings/ko-KR':
            return {'language': 'ko-KR'}
        if path.endswith(':validate'):
            return {'id': edit}
        if ':commit?' in path:
            if self.refuse_commit:
                raise listing.ListingError('Google Play edit_commit failed (HTTP 400)')
            self.remote = {t: list(self.working[edit][t]) for t, _ in listing.ASSET_TYPES}
            self.working.pop(edit, None)
            return {'id': edit}
        kind = next((k for k, _ in listing.ASSET_TYPES if f'/listings/ko-KR/{k}' in path), None)
        if not kind:
            raise AssertionError(f'Unexpected Play endpoint: {path}')
        if method == 'DELETE':
            self.working[edit][kind] = []
            return {}
        if method == 'GET':
            return {'images': [{'sha256': digest} for digest in self.working[edit][kind]]}
        if method == 'POST' and media and path.endswith('?uploadType=media'):
            digest = hashlib.sha256(payload).hexdigest()
            self.working[edit][kind].append(digest)
            return {'image': {'id': digest[:12], 'sha256': digest}}
        raise AssertionError(f'Unexpected Play call: {method} {path}')


class ListingImagesTest(unittest.TestCase):
    def setUp(self):
        self.assets = listing.validate_assets()

    def test_exact_ten_files_and_hashes(self):
        self.assertEqual(len(self.assets), 10)
        self.assertEqual(list(self.assets), list(listing.SPECS))

    def test_hash_tampering_rejected_before_upload(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            for name in ['manifest.json', *self.assets.keys()]:
                shutil.copy2(listing.ASSETS / name, folder / name)
            modified = folder / '02-workspace.png'
            modified.write_bytes(modified.read_bytes() + b'broken')
            with self.assertRaisesRegex(listing.ListingError, 'hash/size mismatch'):
                listing.validate_assets(folder)

    def test_staged_image_upload_and_fresh_sha_readback(self):
        client = FakePlay()
        message = listing.submit(client, self.assets)
        self.assertIn('rechecked', message)
        for kind, names in listing.ASSET_TYPES:
            self.assertEqual(client.remote[kind], [self.assets[n]['sha256'] for n in names])
        self.assertEqual(len([p for method, p, _ in client.calls if method == 'POST' and ':commit?' in p]), 1)
        self.assertTrue(any('changesInReviewBehavior=ERROR_IF_IN_REVIEW' in p and 'changesNotSentForReview=true' in p for _, p, _ in client.calls))
        self.assertTrue(all('/tracks/' not in p and '/bundles' not in p and '/apks' not in p for _, p, _ in client.calls))
        self.assertTrue(all('/listings/' not in p or '/listings/ko-KR' in p for _, p, _ in client.calls))
        self.assertFalse(client.working)

    def test_review_hold_aborts_without_modifying_published_listing(self):
        before = {t: ['existing-sha'] for t, _ in listing.ASSET_TYPES}
        client = FakePlay(remote={k:list(v) for k,v in before.items()}, refuse_commit=True)
        with self.assertRaisesRegex(listing.ListingError, 'HTTP 400'):
            listing.submit(client, self.assets)
        self.assertEqual(client.remote, before)
        self.assertFalse(client.working)


if __name__ == '__main__':
    unittest.main()
