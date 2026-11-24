#!/usr/bin/env python3
"""Read-only release gate for one already-uploaded Google Play internal AAB.

Starts/discards an ephemeral Play edit. Never uploads, commits, changes a track,
adds testers, or asserts Console approval or physical-device installability.
"""
import argparse
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path

from publisher import HttpTransport, PACKAGE, Publisher, ReleaseError, version_code


def verify_internal_release(track, bundles, expected_code, expected_sha256):
    code = version_code(expected_code)
    expected = str(expected_sha256).lower()
    if not re.fullmatch(r"[a-f0-9]{64}", expected):
        raise ReleaseError("Expected AAB SHA-256 must contain exactly 64 hexadecimal digits")
    if not isinstance(track, dict) or track.get("track") != "internal":
        raise ReleaseError("Unexpected track response; the internal release was not verified")

    entries = track.get("releases", [])
    completed = [
        item for item in entries
        if isinstance(item, dict)
        and item.get("status") == "completed"
        and str(code) in [str(v) for v in item.get("versionCodes", [])]
    ]
    if len(completed) != 1:
        raise ReleaseError("Expected completed internal versionCode was not found exactly once")

    matched = [
        entry for entry in bundles
        if isinstance(entry, dict) and str(entry.get("versionCode")) == str(code)
    ]
    if len(matched) != 1:
        raise ReleaseError("Expected uploaded bundle versionCode was not found exactly once")
    actual = matched[0].get("sha256")
    if not isinstance(actual, str) or actual.lower() != expected:
        raise ReleaseError("Uploaded AAB SHA-256 does not match the verified release identity")

    return {
        "package": PACKAGE,
        "track": "internal",
        "versionCode": code,
        "releaseStatus": "completed",
        "uploadedBundleSHA256": expected,
        "status": "api_readonly_verified",
        "googlePlayConsoleApproval": "not_verified",
        "testerOptIn": "not_verified",
        "physicalDeviceInstall": "not_verified",
        "talkBackAndFoldable": "not_verified",
        "changesSubmitted": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version-code", required=True, type=version_code)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    try:
        token = os.environ.get("PLAY_ACCESS_TOKEN", "")
        publisher = Publisher(HttpTransport(token))
        # POST creates a transient edit; only GET and DELETE follow. No commit.
        with publisher.edit(ignore_delete_error=False) as edit:
            track = publisher.request("GET", edit + "/tracks/internal")
            bundles = publisher.request("GET", edit + "/bundles").get("bundles", [])
        report = verify_internal_release(track, bundles, args.version_code, args.sha256)
        report["checkedAtUTC"] = datetime.now(timezone.utc).isoformat()
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            json.dumps(report, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        print(json.dumps(report, ensure_ascii=False))
        return 0
    except (ReleaseError, KeyError, OSError, TypeError, ValueError) as error:
        # Prevent token/listing data from appearing in CI error reports.
        if isinstance(error, ReleaseError):
            print("RELEASE-A07 read-only verification blocked: " + str(error))
        else:
            print("RELEASE-A07 read-only verification blocked: invalid API response or report path")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
