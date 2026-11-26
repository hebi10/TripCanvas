import base64
import contextlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import urllib.error
from unittest import mock

sys.path.insert(0, str(Path(__file__).parents[1]))
import publisher
import validation_diagnostics as diagnostics
from test_publisher import PlayServer


def validation_error(request, response, status=400):
    raise urllib.error.HTTPError(request.full_url, status, 'private HTTP reason', {}, io.BytesIO(response))


class ValidationDiagnosticTests(unittest.TestCase):
    def test_validation_uses_empty_request_and_never_commits_when_rejected(self):
        server = PlayServer()

        def fail(request, **kwargs):
            self.assertIsNone(request.data)
            self.assertFalse(request.has_header('Content-type'))
            validation_error(request, b'{"error":{"message":"private response"}}')

        transport = publisher.HttpTransport('private token', opener=fail)

        def request(method, path, body=None, content_type=None):
            if path.endswith(':validate'):
                return transport(method, path, body, content_type)
            return server(method, path, body, content_type)

        with tempfile.NamedTemporaryFile() as bundle:
            bundle.write(b'test bundle')
            bundle.flush()
            with self.assertRaises(publisher.PlayApiError) as failed:
                publisher.Publisher(request).deploy(bundle.name, 35)
        self.assertEqual(failed.exception.stage, 'validate')
        self.assertEqual(server.calls[-1], ('DELETE', '/edits/1', None))
        self.assertFalse(server.committed)

    def test_bad_or_unrelated_responses_do_not_produce_encrypted_diagnostics(self):
        good = b'{"error":{"message":"private response"}}'
        cases = [(400, 'POST', '/edits/1:validate', b'not json'),
                 (400, 'POST', '/edits/1:validate', good + b' ' * 65536),
                 (500, 'POST', '/edits/1:validate', good),
                 (400, 'POST', '/edits/1:commit', good)]
        for status, method, path, raw in cases:
            with self.subTest(status=status, path=path, size=len(raw)):
                write = mock.Mock()
                def fail(request, **kwargs):
                    validation_error(request, raw, status)
                with self.assertRaises(publisher.PlayApiError) as failure:
                    publisher.HttpTransport('private token', fail, write)(method, path)
                self.assertIsNone(failure.exception.validation_summary)
                write.assert_not_called()

    def test_invalid_opt_in_recipient_is_rejected_before_transport(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_file = Path(tmp) / 'validation-error.der'
            args = ['publisher.py', 'deploy', '--bundle', 'unused.aab', '--version-code', '35',
                    '--validation-diagnostic-output', str(output_file)]
            env = {'PLAY_CONSOLE_INITIALIZED': 'true',
                   'PLAY_VALIDATION_DIAGNOSTIC_CERT_DER_B64': 'private-not-a-certificate'}
            output, errors = io.StringIO(), io.StringIO()
            with mock.patch.dict(os.environ, env), mock.patch.object(publisher, 'HttpTransport') as transport, \
                    mock.patch('sys.argv', args), contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
                self.assertEqual(publisher.main(), 1)
            transport.assert_not_called()
            self.assertFalse(output_file.exists())
            self.assertNotIn(env['PLAY_VALIDATION_DIAGNOSTIC_CERT_DER_B64'], output.getvalue() + errors.getvalue())
            self.assertEqual(json.loads(output.getvalue())['stage'], 'diagnostic_setup')

    def test_encryption_error_leaves_no_plaintext_or_artifact(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(
                diagnostics, 'run_openssl', side_effect=subprocess.CalledProcessError(1, 'openssl')):
            output = Path(tmp) / 'validation-error.der'
            self.assertFalse(diagnostics.save_encrypted_message('public certificate', output, 'private response'))
            self.assertEqual(list(Path(tmp).iterdir()), [])

    @unittest.skipUnless(shutil.which('openssl', path=os.defpath), 'OpenSSL is required for optional diagnostics')
    def test_only_fixed_counts_are_reported_and_opt_in_message_is_ciphertext(self):
        message = 'private Play response with Korean UTF-8 검증 ' + '가' * 1800
        payload = {'error': {'status': 'INVALID_ARGUMENT', 'message': message,
                            'errors': [{'domain': 'global', 'reason': 'badRequest'},
                                       {'domain': 'global', 'reason': 'private dynamic identifier'}],
                            'details': [{'@type': 'type.googleapis.com/google.rpc.BadRequest',
                                         'fieldViolations': [{'field': 'private field', 'description': 'private detail'}]},
                                        {'@type': 'private detail type', 'metadata': {'secret': 'private value'}}]}}
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            cert, private = directory / 'cert.pem', directory / 'key.pem'
            subprocess.run(['openssl', 'req', '-new', '-newkey', 'rsa:3072', '-nodes', '-x509',
                            '-days', '1', '-subj', '/CN=TripCanvas test diagnostic only',
                            '-out', str(cert), '-keyout', str(private)], capture_output=True, check=True)
            der = diagnostics.run_openssl(['x509', '-outform', 'DER'], cert.read_bytes())
            destination = directory / 'validation-error.der'
            report = directory / 'report.json'
            args = ['publisher.py', '--report', str(report), 'deploy', '--bundle', 'unused.aab',
                    '--version-code', '35', '--validation-diagnostic-output', str(destination)]
            env = {'PLAY_CONSOLE_INITIALIZED': 'true', 'PLAY_ACCESS_TOKEN': 'private test token',
                   'PLAY_VALIDATION_DIAGNOSTIC_CERT_DER_B64': base64.b64encode(der).decode('ascii')}

            def fail(request, **kwargs):
                validation_error(request, json.dumps(payload).encode())

            transport = publisher.HttpTransport
            def configured(token, **kwargs):
                return transport(token, opener=fail, **kwargs)

            def stage(client, *args, **kwargs):
                with client.operation_stage('validate'):
                    client.request('POST', '/edits/88:validate')

            output, errors = io.StringIO(), io.StringIO()
            with mock.patch.dict(os.environ, env), mock.patch.object(publisher, 'HttpTransport', configured), \
                    mock.patch.object(publisher.Publisher, 'deploy', stage), mock.patch('sys.argv', args), \
                    contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
                self.assertEqual(publisher.main(), 1)
            receipt = json.loads(report.read_bytes())
            self.assertEqual(receipt['validationDiagnostics'], {'legacyReasons': ['badRequest'],
                             'detailCounts': {'badRequest': 1}, 'violationCounts': {'badRequest': 1},
                             'encryptedMessage': 'saved'})
            self.assertEqual(receipt['commitOutcome'], 'not_attempted')
            ciphertext = destination.read_bytes()
            observed = output.getvalue().encode() + errors.getvalue().encode() + report.read_bytes() + ciphertext
            self.assertNotIn(b'private', observed)
            clear = diagnostics.run_openssl(['cms', '-decrypt', '-binary', '-inform', 'DER',
                                             '-recip', str(cert), '-inkey', str(private)], ciphertext)
            contents = json.loads(clear)
            self.assertEqual(set(contents), {'stage', 'httpStatus', 'googleErrorMessage', 'truncated'})
            self.assertTrue(contents['truncated'])
            self.assertEqual(contents['googleErrorMessage'], message.encode()[:4096].decode('utf-8', errors='ignore'))
            self.assertFalse(any(b'private Play response' in f.read_bytes() for f in directory.iterdir()))


if __name__ == '__main__':
    unittest.main()
