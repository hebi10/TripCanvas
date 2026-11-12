"""Offline Play listing update guards; never contacts Google Play."""
import hashlib
import io
import json
import urllib.error
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
    def __init__(self, remote=None, refuse_commit=False, refuse_reason=None):
        self.refuse_reason = refuse_reason
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
                raise listing.PlayApiError('edit_commit', 400, self.refuse_reason)
            if self.refuse_reason == listing.REVIEW_HOLD_FORBIDDEN and 'changesNotSentForReview=true' in path:
                raise listing.PlayApiError('edit_commit', 400, listing.REVIEW_HOLD_FORBIDDEN)
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
        client = FakePlay(remote={k: list(v) for k, v in before.items()},
                          refuse_commit=True, refuse_reason=listing.CHANGES_ALREADY_IN_REVIEW)
        with self.assertRaisesRegex(listing.ListingError, 'CHANGES_ALREADY_IN_REVIEW'):
            listing.submit(client, self.assets)
        self.assertEqual(client.remote, before)
        self.assertEqual(sum(':commit?' in path for _, path, _ in client.calls), 1)
        self.assertFalse(client.working)

    def test_unsupported_hold_uses_one_safe_retry(self):
        client = FakePlay(refuse_reason=listing.REVIEW_HOLD_FORBIDDEN)
        listing.submit(client, self.assets)
        commits = [path for _, path, _ in client.calls if ':commit?' in path]
        self.assertEqual(len(commits), 2)
        self.assertIn('changesNotSentForReview=true', commits[0])
        self.assertNotIn('changesNotSentForReview=', commits[1])
        self.assertIn('changesInReviewBehavior=ERROR_IF_IN_REVIEW', commits[1])
        self.assertFalse(client.working)

    def test_unknown_commit_failure_does_not_retry(self):
        client = FakePlay(refuse_commit=True)
        with self.assertRaisesRegex(listing.ListingError, 'HTTP 400'):
            listing.submit(client, self.assets)
        self.assertEqual(sum(':commit?' in path for _, path, _ in client.calls), 1)

    def test_http_error_only_discloses_allowlisted_reason(self):
        secret = 'never-print-private-play-error'
        for payload, expected in (
            ({'error': {'message': listing.REVIEW_HOLD_FORBIDDEN_MESSAGE}}, listing.REVIEW_HOLD_FORBIDDEN),
            ({'error': {'details': [{'reason': listing.CHANGES_ALREADY_IN_REVIEW}]}},
             listing.CHANGES_ALREADY_IN_REVIEW),
            ({'error': {'message': secret, 'details': [{'reason': 'UNKNOWN_PRIVATE_REASON'}]}}, None),
        ):
            with self.subTest(expected=expected):
                def deny(request, **_):
                    return_value = io.BytesIO(json.dumps(payload).encode('utf-8'))
                    raise urllib.error.HTTPError(request.full_url, 400, secret, {}, return_value)
                with self.assertRaises(listing.PlayApiError) as ctx:
                    listing.PlayClient('private-test-token', opener=deny).request(
                        'POST', '/edits/123:commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW',
                        {}, stage='edit_commit')
                self.assertEqual(ctx.exception.safe_reason, expected)
                self.assertNotIn(secret, str(ctx.exception))
                self.assertNotIn('private-test-token', str(ctx.exception))


class FinalReleasePlay(FakePlay):
    """In-memory two-track + listing edit API, including atomic commit failure."""
    def __init__(self, *, wrong_bundle=False, missing_internal=False, refuse_commit=False):
        super().__init__(refuse_commit=refuse_commit)
        self.wrong_bundle = wrong_bundle
        self.missing_internal = missing_internal
        self.alpha = {'releases': [{'status': 'completed', 'versionCodes': ['3']}]}
        self.pending_alpha = {}

    def request(self, method, path, payload=None, *, media=False, stage='request'):
        if method == 'POST' and path == '/edits':
            result = super().request(method, path, payload, media=media, stage=stage)
            self.pending_alpha[result['id']] = json.loads(json.dumps(self.alpha))
            return result

        parts = path.split('/')
        if len(parts) >= 3 and parts[1] == 'edits':
            edit = parts[2].split(':')[0]
            if method == 'GET' and path == f'/edits/{edit}/bundles':
                self.calls.append((method, path, media))
                return {'bundles': [{
                    'versionCode': 10,
                    'sha256': '0' * 64 if self.wrong_bundle else listing.R09_AAB_SHA256,
                }]}
            if method == 'GET' and path == f'/edits/{edit}/tracks/internal':
                self.calls.append((method, path, media))
                return {'releases': [] if self.missing_internal else [{
                    'name': '0.1.0 (10)', 'status': 'completed', 'versionCodes': ['10'],
                }]}
            if method == 'PUT' and path == f'/edits/{edit}/tracks/alpha':
                self.calls.append((method, path, media))
                self.pending_alpha[edit] = json.loads(json.dumps(payload))
                return payload
            if method == 'GET' and path == f'/edits/{edit}/tracks/alpha':
                self.calls.append((method, path, media))
                return json.loads(json.dumps(self.pending_alpha[edit]))
            if method == 'POST' and ':commit?' in path:
                pending = json.loads(json.dumps(self.pending_alpha[edit]))
                result = super().request(method, path, payload, media=media, stage=stage)
                self.alpha = pending
                return result
            if method == 'DELETE' and path == f'/edits/{edit}':
                self.pending_alpha.pop(edit, None)

        return super().request(method, path, payload, media=media, stage=stage)


class FinalReleaseTest(unittest.TestCase):
    def setUp(self):
        self.assets = listing.validate_assets()

    def test_one_edit_atomically_updates_images_and_existing_v10_alpha(self):
        client = FinalReleasePlay()
        result = listing.publish_r09(client, self.assets)
        self.assertEqual((result['track'], result['versionCode'], result['status']),
                         ('alpha', 10, 'api_committed_and_verified'))
        for image_type, names in listing.ASSET_TYPES:
            self.assertEqual(client.remote[image_type],
                             [self.assets[name]['sha256'] for name in names])
        self.assertEqual(client.alpha['releases'][0]['versionCodes'], ['10'])
        commits = [p for method, p, _ in client.calls if ':commit?' in p]
        self.assertEqual(len(commits), 1)
        self.assertIn('changesInReviewBehavior=ERROR_IF_IN_REVIEW', commits[0])
        self.assertNotIn('CANCEL_IN_REVIEW', commits[0])
        self.assertFalse(any(method == 'POST' and '/bundles' in path
                             for method, path, _ in client.calls))
        self.assertFalse(any('/tracks/production' in path for _, path, _ in client.calls))
        self.assertFalse(client.working)

    def test_wrong_aab_fails_before_any_upload(self):
        for client in (FinalReleasePlay(wrong_bundle=True),
                       FinalReleasePlay(missing_internal=True)):
            with self.assertRaises(listing.ListingError):
                listing.publish_r09(client, self.assets)
            self.assertFalse(any(media for _, _, media in client.calls))
            self.assertFalse(any(':commit?' in path for _, path, _ in client.calls))
            self.assertFalse(client.working)

    def test_review_in_progress_preserves_both_existing_tracks_and_images(self):
        client = FinalReleasePlay(refuse_commit=True)
        original_alpha = json.loads(json.dumps(client.alpha))
        original_images = {t: list(values) for t, values in client.remote.items()}
        with self.assertRaisesRegex(listing.ListingError, 'HTTP 400'):
            listing.publish_r09(client, self.assets)
        self.assertEqual(client.remote, original_images)
        self.assertEqual(client.alpha, original_alpha)
        self.assertEqual(sum(':commit?' in path for _, path, _ in client.calls), 1)
        self.assertFalse(client.working)


if __name__ == '__main__':
    unittest.main()
