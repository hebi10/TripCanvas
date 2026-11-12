"""Offline unit tests: RELEASE-A07 never modifies the Play release."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from check_a07_readonly import verify_internal_release
from publisher import ReleaseError


HASH = "3a25d3c4bf9c9e8963c976f814f66ca6bcc65706a8c07da1e139af18e0b247dc"
CODE = 15


class ReleaseA07ReadOnlyTests(unittest.TestCase):
    def setUp(self):
        self.track = {
            "track": "internal",
            "releases": [{"status": "completed", "versionCodes": ["15"], "name": "0.1.0 (15)"}],
        }
        self.bundles = [{"versionCode": 15, "sha256": HASH}]

    def test_completed_internal_bundle_matches_verified_sha_and_does_not_claim_install(self):
        result = verify_internal_release(self.track, self.bundles, CODE, HASH.upper())
        self.assertEqual(result["status"], "api_readonly_verified")
        self.assertEqual(result["versionCode"], CODE)
        self.assertEqual(result["releaseStatus"], "completed")
        self.assertEqual(result["uploadedBundleSHA256"], HASH)
        self.assertEqual(result["physicalDeviceInstall"], "not_verified")
        self.assertEqual(result["testerOptIn"], "not_verified")
        self.assertEqual(result["googlePlayConsoleApproval"], "not_verified")
        self.assertFalse(result["changesSubmitted"])

    def test_missing_or_draft_track_is_rejected(self):
        for releases in (
            [],
            [{"status": "draft", "versionCodes": ["15"]}],
            [{"status": "completed", "versionCodes": ["14"]}],
        ):
            with self.subTest(releases=releases):
                with self.assertRaises(ReleaseError):
                    verify_internal_release({"track": "internal", "releases": releases}, self.bundles, CODE, HASH)

    def test_tampered_or_missing_aab_is_rejected(self):
        for bundles in (
            [],
            [{"versionCode": 15, "sha256": "0" * 64}],
            [{"versionCode": 14, "sha256": HASH}],
            [{"versionCode": 15, "sha256": HASH}] * 2,
        ):
            with self.subTest(bundles=bundles):
                with self.assertRaises(ReleaseError):
                    verify_internal_release(self.track, bundles, CODE, HASH)

    def test_wrong_track_and_duplicate_completed_releases_are_rejected(self):
        with self.assertRaises(ReleaseError):
            verify_internal_release({"track": "production", "releases": self.track["releases"]}, self.bundles, CODE, HASH)
        with self.assertRaises(ReleaseError):
            verify_internal_release(
                {"track": "internal", "releases": self.track["releases"] * 2},
                self.bundles, CODE, HASH,
            )

    def test_bad_hash_or_version_is_rejected(self):
        with self.assertRaises(ReleaseError):
            verify_internal_release(self.track, self.bundles, CODE, "not-a-hash")
        for invalid in (0, -1):
            with self.assertRaises(ReleaseError):
                verify_internal_release(self.track, self.bundles, invalid, HASH)


if __name__ == "__main__":
    unittest.main()
