"""Bounded diagnostics for Play edit validation failures."""
import base64
import json
import os
import re
import ssl
import subprocess
import tempfile
from pathlib import Path


LEGACY_REASONS = {'badRequest', 'invalid', 'invalidParameter', 'required', 'conditionNotMet'}
DETAIL_TYPES = {
    'type.googleapis.com/google.rpc.BadRequest': ('badRequest', 'fieldViolations'),
    'type.googleapis.com/google.rpc.PreconditionFailure': ('preconditionFailure', 'violations'),
    'type.googleapis.com/google.rpc.ErrorInfo': ('errorInfo', None),
}
CERT_INPUT_MAX = 4096
MESSAGE_BYTES_MAX = 4096


def summary(error):
    """Expose only fixed categories and counts, never response text or identifiers."""
    legacy_reasons = set()
    legacy_errors = error.get('errors')
    if isinstance(legacy_errors, list):
        for item in legacy_errors:
            if isinstance(item, dict) and item.get('domain') == 'global':
                reason = item.get('reason')
                if isinstance(reason, str) and reason in LEGACY_REASONS:
                    legacy_reasons.add(reason)

    detail_types, violations = {}, {}
    details = error.get('details')
    if isinstance(details, list):
        for item in details:
            kind = item.get('@type') if isinstance(item, dict) else None
            if not isinstance(kind, str) or kind not in DETAIL_TYPES:
                continue
            name, field = DETAIL_TYPES[kind]
            detail_types[name] = detail_types.get(name, 0) + 1
            problems = item.get(field) if field else None
            if isinstance(problems, list):
                violations[name] = violations.get(name, 0) + len(problems)
    return {'legacyReasons': sorted(legacy_reasons), 'detailCounts': detail_types,
            'violationCounts': violations}


def run_openssl(args, data):
    # The subprocess must not inherit Play credentials, and its output is never logged.
    return subprocess.run(['openssl', *args], input=data, stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL, env={'PATH': os.defpath, 'LC_ALL': 'C'},
                          timeout=15, check=True).stdout


def recipient_certificate(encoded):
    """Accept exactly one base64 DER certificate carrying a 3072-bit or larger RSA key."""
    if len(encoded) > CERT_INPUT_MAX:
        raise ValueError('Invalid validation diagnostic public certificate')
    try:
        der = base64.b64decode(encoded, validate=True)
        if run_openssl(['x509', '-inform', 'DER', '-outform', 'DER'], der) != der:
            raise ValueError('Expected one DER certificate')
        public_key = run_openssl(['x509', '-inform', 'DER', '-pubkey', '-noout'], der)
        rsa = run_openssl(['rsa', '-pubin', '-text', '-noout'], public_key)
        key_size = re.match(rb'Public-Key: \((\d+) bit\)', rsa)
        if key_size is None or int(key_size.group(1)) < 3072:
            raise ValueError('Invalid RSA key size')
    except (OSError, ValueError, subprocess.SubprocessError):
        raise ValueError('Invalid validation diagnostic public certificate') from None
    return ssl.DER_cert_to_PEM_cert(der)


def save_encrypted_message(recipient, destination, message):
    if not isinstance(message, str) or not message:
        return False
    original = message.encode('utf-8', errors='replace')
    data = json.dumps({'stage': 'validate', 'httpStatus': 400,
                       'googleErrorMessage': original[:MESSAGE_BYTES_MAX].decode('utf-8', errors='ignore'),
                       'truncated': len(original) > MESSAGE_BYTES_MAX}, ensure_ascii=False).encode('utf-8')
    try:
        with tempfile.TemporaryDirectory() as directory:
            cert = Path(directory) / 'recipient.pem'
            cert.write_text(recipient, encoding='ascii')
            encrypted = run_openssl(['cms', '-encrypt', '-aes-256-gcm', '-binary', '-outform', 'DER',
                                     '-recip', str(cert), '-keyopt', 'rsa_padding_mode:oaep',
                                     '-keyopt', 'rsa_oaep_md:sha256', '-keyopt', 'rsa_mgf1_md:sha256'], data)
        if not encrypted:
            return False
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as output:
                temporary = Path(output.name)
                output.write(encrypted)
            temporary.replace(destination)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
    except (OSError, subprocess.SubprocessError):
        return False
    return True
