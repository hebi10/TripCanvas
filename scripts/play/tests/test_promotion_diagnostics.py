"""Redacted Play alpha-promotion diagnostics; no live Play API calls."""
import contextlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
import urllib.error
from unittest import mock

sys.path.insert(0, str(Path(__file__).parents[1]))
import publisher
from test_publisher import PlayServer, track


class PromotionDiagnosticsTests(unittest.TestCase):
    def test_http_errors_identify_fixed_operation_without_response_or_edit_id(self):
        secret = 'private response containing an account and bearer token'
        response = json.dumps({'error': {'status': 'INVALID_ARGUMENT', 'message': secret}})
        cases = [
            ('POST', '/edits/privateedit:validate', 'edit_validate'),
            ('POST', '/edits/privateedit:commit?changesNotSentForReview=false', 'edit_commit'),
            ('PUT', '/edits/privateedit/tracks/alpha', 'alpha_track_update'),
            ('POST', '/edits', 'edit_create'),
            ('POST', '/edits/privateedit/unexpected', 'unclassified'),
        ]
        for method, path, operation in cases:
            def fail(request, **kwargs):
                raise urllib.error.HTTPError(request.full_url, 400, secret, {},
                                             io.BytesIO(response.encode('utf-8')))
            with self.subTest(operation=operation), self.assertRaises(publisher.PlayApiError) as raised:
                publisher.HttpTransport('secret-token', fail)(method, path, {})
            error = raised.exception
            self.assertEqual(error.operation, operation)
            self.assertEqual(error.http_status, 400)
            self.assertEqual(error.api_status, 'INVALID_ARGUMENT')
            self.assertNotIn(secret, str(error))
            self.assertNotIn('privateedit', str(error))
            self.assertNotIn('secret-token', str(error))

    def test_promote_validation_400_creates_redacted_report_without_commit_or_upload(self):
        server = PlayServer(bundles=[3], tracks=[track('internal', 3)])
        def request(method, path, body=None, content_type=None):
            if method == 'POST' and path.endswith(':validate'):
                raise publisher.PlayApiError(method, 400, 'INVALID_ARGUMENT',
                                             operation='edit_validate')
            return server(method, path, body, content_type)

        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'play-promotion.json'
            argv = ['publisher.py', '--report', str(report), 'promote', '--version-code', '3', '--track', 'alpha']
            stdout, stderr = io.StringIO(), io.StringIO()
            with mock.patch.dict(os.environ, {'PLAY_ACCESS_TOKEN': 'secret-token',
                                               'PLAY_CONSOLE_INITIALIZED': 'true'}), \
                 mock.patch.object(publisher, 'HttpTransport', return_value=request), \
                 mock.patch('sys.argv', argv), contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                self.assertEqual(publisher.main(), 1)
            result = json.loads(report.read_text(encoding='utf-8'))
            self.assertEqual(result['track'], 'alpha')
            self.assertEqual(result['versionCode'], 3)
            self.assertEqual(result['status'], 'promotion_not_verified')
            self.assertEqual(result['deployment'], 'unconfirmed')
            self.assertEqual(result['failedOperation'], 'edit_validate')
            self.assertEqual(result['httpStatus'], 400)
            self.assertEqual(result['apiStatus'], 'INVALID_ARGUMENT')
            self.assertNotIn('secret-token', report.read_text() + stdout.getvalue() + stderr.getvalue())
        self.assertFalse(any('uploadType' in path or ':commit' in path for _, path, _ in server.calls))
        self.assertFalse(server.committed)

    def test_failed_commit_is_unconfirmed_even_when_track_write_succeeded(self):
        server = PlayServer(bundles=[3], tracks=[track('internal', 3)])
        def request(method, path, body=None, content_type=None):
            if method == 'POST' and path.endswith(':commit'):
                raise publisher.PlayApiError(method, 400, 'INVALID_ARGUMENT',
                                             operation='edit_commit')
            return server(method, path, body, content_type)
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'report.json'
            args = ['publisher.py', '--report', str(report), 'promote', '--version-code', '3']
            with mock.patch.dict(os.environ, {'PLAY_ACCESS_TOKEN': 'secret-token',
                                               'PLAY_CONSOLE_INITIALIZED': 'true'}), \
                 mock.patch.object(publisher, 'HttpTransport', return_value=request), \
                 mock.patch('sys.argv', args), contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(publisher.main(), 1)
            data = json.loads(report.read_text(encoding='utf-8'))
            self.assertEqual(data['failedOperation'], 'edit_commit')
            self.assertEqual(data['deployment'], 'unconfirmed')
            self.assertEqual(data['httpStatus'], 400)
        self.assertFalse(server.committed)
        self.assertFalse(any('uploadType' in path for _, path, _ in server.calls))


if __name__ == '__main__':
    unittest.main()
