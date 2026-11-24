"""OPS-05 offline regression tests — never contact Google Play."""
import importlib.util
import os
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "ops05" / "promotion.py"
spec = importlib.util.spec_from_file_location("ops05", SCRIPT)
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class PromotionTests(unittest.TestCase):
    def env(self, mode="promote"):
        return {"GITHUB_EVENT_NAME":"workflow_dispatch","GITHUB_REF":"refs/heads/main",
                "GITHUB_REPOSITORY":"hebi10/fixture","EXPECTED_REPO":"hebi10/fixture",
                "GITHUB_SHA":"a"*40,"EXPECTED_MAIN_SHA":"a"*40,
                "BATCH_ID":"2026-11-13-fixture","APP_PACKAGE":"com.example.fixture",
                "INTERNAL_TRACK":"internal","CLOSED_TRACK":"alpha",
                "VERSION_CODE":"19","AAB_SHA256":"b"*64,"MODE":mode,
                "FINAL_BATCH_APPROVED":"true","APPROVAL_PHRASE":p.APPROVAL}

    def snapshot(self, source_status="completed", closed=None, bundle_hash=None):
        return ([{"track":"internal","releases":[{"status":source_status,"versionCodes":["19"],"name":"1.0 (19)"}]},
                 {"track":"alpha","releases":closed if closed is not None else []}],
                [{"versionCode":19,"sha256":bundle_hash or "b"*64}])

    def test_unapproved_mutation_rejected(self):
        env = self.env()
        env["FINAL_BATCH_APPROVED"] = "false"
        with self.assertRaises(p.Stop):
            p.parse_request(env)

    def test_wrong_source_sha_rejected(self):
        env = self.env()
        env["EXPECTED_MAIN_SHA"] = "c"*40
        with self.assertRaises(p.Stop):
            p.parse_request(env)

    def test_check_is_allowed_without_promotion_approval(self):
        env = self.env("check")
        env["FINAL_BATCH_APPROVED"] = "false"
        env["APPROVAL_PHRASE"] = ""
        self.assertEqual(p.parse_request(env)["mode"], "check")

    def test_promote_request_can_run_readonly_check_first(self):
        source = Path(SCRIPT).read_text(encoding="utf-8")
        self.assertIn('sys.argv[1] == "check" or sys.argv[1] == req["mode"]', source)

    def test_forbidden_destination_rejected(self):
        for track in ("production","beta","internal","qa","open","internalsharing"):
            with self.subTest(track=track):
                env = self.env()
                env["CLOSED_TRACK"] = track
                with self.assertRaises(p.Stop):
                    p.parse_request(env)

    def test_valid_exact_bundle_and_completed_internal(self):
        req = p.parse_request(self.env())
        tracks, bundles = self.snapshot()
        self.assertEqual(p.make_plan(tracks,bundles,req)["state"],"ready")

    def test_internal_draft_rejected(self):
        req = p.parse_request(self.env())
        tracks,bundles=self.snapshot(source_status="draft")
        with self.assertRaises(p.Stop):
            p.make_plan(tracks,bundles,req)

    def test_wrong_aab_sha256_rejected(self):
        req=p.parse_request(self.env())
        tracks,bundles=self.snapshot(bundle_hash="f"*64)
        with self.assertRaises(p.Stop):
            p.make_plan(tracks,bundles,req)

    def test_missing_existing_closed_track_rejected(self):
        req=p.parse_request(self.env())
        tracks,bundles=self.snapshot()
        with self.assertRaises(p.Stop):
            p.make_plan(tracks[:1],bundles,req)

    def test_closed_pending_review_rejected(self):
        req=p.parse_request(self.env())
        tracks,bundles=self.snapshot(closed=[{"status":"draft","versionCodes":[]}])
        with self.assertRaises(p.Stop):
            p.make_plan(tracks,bundles,req)

    def test_closed_downgrade_rejected(self):
        req=p.parse_request(self.env())
        tracks,bundles=self.snapshot(closed=[{"status":"completed","versionCodes":["20"]}])
        with self.assertRaises(p.Stop):
            p.make_plan(tracks,bundles,req)

    def test_existing_identical_closed_version_no_change(self):
        req=p.parse_request(self.env())
        tracks,bundles=self.snapshot(closed=[{"status":"completed","versionCodes":["19"]}])
        self.assertEqual(p.make_plan(tracks,bundles,req)["state"],"already_completed")

    def test_duplicate_bundle_rejected(self):
        req=p.parse_request(self.env())
        tracks,bundles=self.snapshot()
        with self.assertRaises(p.Stop):
            p.make_plan(tracks,bundles+bundles,req)

    def test_check_mode_never_writes_or_commits_a_play_track(self):
        from unittest.mock import patch
        env = self.env("check")
        req = p.parse_request(env)
        tracks, bundles = self.snapshot()
        calls = []
        class FakePlay:
            def __init__(self, token, package):
                self.package = package
            def start(self):
                calls.append("start")
                return "/temporary"
            def inventory(self, edit):
                calls.append("inventory")
                return tracks, bundles
            def discard(self, edit):
                calls.append("discard")
            def request(self, method, suffix, data=None):
                raise AssertionError("check must not PUT or commit")
        with patch.object(p, "Play", FakePlay):
            receipt = p.execute("check", req, "redacted-fixture", env)
        self.assertEqual(receipt["status"], "verified_ready_for_manual_promotion")
        self.assertEqual(calls, ["start", "inventory", "discard"])

    def test_promote_updates_only_closed_track_with_same_code_and_review_protection(self):
        from unittest.mock import patch
        env = self.env("promote")
        req = p.parse_request(env)
        before_tracks, bundles = self.snapshot()
        after_tracks, _ = self.snapshot(closed=[{"status":"completed","versionCodes":["19"]}])
        calls = []
        class FakePlay:
            def __init__(self, token, package):
                self.starts = 0
            def start(self):
                self.starts += 1
                return "/temporary" + str(self.starts)
            def inventory(self, edit):
                return (before_tracks, bundles) if self.starts == 1 else (after_tracks, bundles)
            def discard(self, edit):
                calls.append(("DELETE",edit))
            def request(self, method, suffix, data=None):
                calls.append((method,suffix))
                if method == "PUT":
                    self.assert_only_closed = suffix.endswith("/tracks/alpha")
                    if not self.assert_only_closed:
                        raise AssertionError("attempt to write non-closed track")
                    if data["releases"][0]["versionCodes"] != ["19"]:
                        raise AssertionError("different AAB version")
                return {}
        with patch.object(p, "Play", FakePlay), patch.object(p,"verify_main"), patch.object(p,"persist"):
            result=p.execute("promote",req,"redacted-fixture",env)
        self.assertEqual(result["status"],"committed_and_read_back")
        self.assertEqual([v[0] for v in calls].count("PUT"),1)
        self.assertIn(("POST","/temporary1:commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW"),calls)
        self.assertNotIn(("POST","/temporary1/bundles"),calls)

    def test_homecrew_requires_existing_special_final_approval(self):
        env=self.env()
        env["APP_PACKAGE"]="com.hebi.homecrew"
        with self.assertRaises(p.Stop):
            p.parse_request(env)
        env["HOMECREW_FINAL_APPROVAL"]="HOMECREW_FINAL_RELEASE_APPROVED"
        self.assertEqual(p.parse_request(env)["package"],"com.hebi.homecrew")

    def test_workflow_is_manual_only_and_never_uploads_aab(self):
        workflow=(ROOT/".github/workflows/play-closed-release.yml").read_text(encoding="utf-8")
        self.assertIn("  workflow_dispatch:",workflow)
        self.assertNotRegex(workflow,r"(?m)^  (push|workflow_run|issue_comment|schedule|pull_request):")
        self.assertIn("inputs.mode == 'promote'",workflow)
        self.assertIn(p.APPROVAL,workflow)
        self.assertNotIn("bundleRelease",workflow)
        self.assertNotIn("bundletool",workflow)
        self.assertNotIn("uploadType",workflow)
        self.assertNotIn("gh release create",workflow)
        self.assertNotIn("actions/download-artifact",workflow)


    def test_http_error_only_keeps_allowlisted_fields(self):
        import io
        import json
        from urllib.error import HTTPError
        private = "fixture-private-message-and-edit-id"
        cases = [
            ({"error": {"status": "INVALID_ARGUMENT", "message": private}}, "INVALID_ARGUMENT"),
            ({"error": {"status": private, "message": private}}, None),
            ({"error": {"status": ["INVALID_ARGUMENT"]}}, None),
            ({"error": {"status": "INVALID_ARGUMENT", "message": "x" * 9000}}, None),
        ]
        for payload, expected in cases:
            with self.subTest(expected=expected):
                def fail(request, **kwargs):
                    raise HTTPError(request.full_url, 400, private, {},
                                    io.BytesIO(json.dumps(payload).encode("utf-8")))
                client = p.Play("fixture-credential", "com.example.fixture", transport=fail)
                with self.assertRaises(p.PlayHttpStop) as raised:
                    client.request("POST", "/fixture-private-edit:validate")
                error = raised.exception
                self.assertEqual(error.http_status, 400)
                self.assertEqual(error.api_status, expected)
                self.assertNotIn(private, str(error))
                self.assertNotIn("fixture-private-edit", str(error))
                self.assertNotIn("fixture-credential", str(error))

    def test_failure_receipts_preserve_phase_and_never_retry_commit(self):
        import json
        from unittest.mock import patch
        env = self.env()
        req = p.parse_request(env)
        before, bundles = self.snapshot()
        after, _ = self.snapshot(closed=[{"status":"completed","versionCodes":["19"]}])
        stages = (
            "edit_create", "inventory_read", "closed_track_update",
            "edit_validate", "edit_commit", "readback_edit_create",
            "readback_inventory", "readback_verify", "readback_edit_discard",
        )
        for stage in stages:
            with self.subTest(stage=stage):
                calls = []
                receipts = []
                class FakePlay:
                    def __init__(self, token, package):
                        self.starts = 0

                    def step(self, name):
                        calls.append(name)
                        if name == stage and name != "readback_verify":
                            raise p.PlayHttpStop(400, "INVALID_ARGUMENT")

                    def start(self):
                        self.starts += 1
                        self.step("edit_create" if self.starts == 1 else "readback_edit_create")
                        return "/fixture-edit-" + str(self.starts)

                    def inventory(self, edit):
                        self.step("inventory_read" if self.starts == 1 else "readback_inventory")
                        return (before, bundles) if self.starts == 1 or stage == "readback_verify" else (after, bundles)

                    def request(self, method, suffix, data=None):
                        if method == "PUT":
                            self.assert_same_version(data)
                            self.step("closed_track_update")
                        elif method == "POST" and suffix.endswith(":validate"):
                            self.step("edit_validate")
                        elif method == "POST" and suffix.endswith(":commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW"):
                            self.step("edit_commit")
                        else:
                            raise AssertionError("Unexpected Play request")
                        return {}

                    def assert_same_version(self, data):
                        if data["track"] != "alpha" or data["releases"][0]["versionCodes"] != ["19"]:
                            raise AssertionError("Wrong track or version")

                    def discard(self, edit):
                        self.step("readback_edit_discard" if edit.endswith("-2") else "edit_discard")

                with patch.object(p, "Play", FakePlay), patch.object(p, "verify_main"), patch.object(
                    p, "persist", side_effect=lambda receipt: receipts.append(dict(receipt))
                ):
                    with self.assertRaises(p.Stop):
                        p.execute("promote", req, "fixture-credential", env)
                self.assertTrue(receipts)
                result = receipts[-1]
                self.assertEqual(result["failedOperation"], stage)
                attempted = stages.index(stage) >= stages.index("edit_commit")
                self.assertEqual(result["deployment"], "unconfirmed" if attempted else "not_committed")
                self.assertEqual(result["status"], "commit_outcome_uncertain_inspect_console_no_retry"
                                 if attempted else "promotion_not_committed")
                self.assertEqual(calls.count("edit_commit"), 1 if attempted else 0)
                if attempted:
                    self.assertNotIn("edit_discard", calls)
                self.assertFalse(result["binary_uploaded"])
                self.assertFalse(result["aab_rebuilt"])
                if stage == "readback_verify":
                    self.assertNotIn("httpStatus", result)
                else:
                    self.assertEqual(result["httpStatus"], 400)
                    self.assertEqual(result["apiStatus"], "INVALID_ARGUMENT")
                self.assertNotIn("fixture-edit-", json.dumps(result))
                self.assertNotIn("fixture-credential", json.dumps(result))

    def test_cleanup_failure_preserves_original_precommit_diagnostic(self):
        from unittest.mock import patch
        env = self.env()
        req = p.parse_request(env)
        tracks, bundles = self.snapshot()
        receipts = []
        calls = []
        class FakePlay:
            def __init__(self, token, package):
                pass

            def start(self):
                return "/fixture-edit"

            def inventory(self, edit):
                return tracks, bundles

            def request(self, method, suffix, data=None):
                calls.append((method, suffix))
                if suffix.endswith(":validate"):
                    raise p.PlayHttpStop(400, "INVALID_ARGUMENT")
                return {}

            def discard(self, edit):
                raise p.PlayHttpStop(503, "UNAVAILABLE")

        with patch.object(p, "Play", FakePlay), patch.object(p, "verify_main"), patch.object(
            p, "persist", side_effect=lambda receipt: receipts.append(dict(receipt))
        ):
            with self.assertRaises(p.PlayHttpStop) as raised:
                p.execute("promote", req, "fixture-credential", env)
        self.assertEqual(raised.exception.http_status, 400)
        self.assertEqual(len(receipts), 1)
        self.assertEqual(receipts[0]["failedOperation"], "edit_validate")
        self.assertEqual(receipts[0]["deployment"], "not_committed")
        self.assertFalse(any(":commit" in suffix for _, suffix in calls))

    def test_readonly_failure_writes_receipt_and_workflow_preserves_it(self):
        from unittest.mock import patch
        env = self.env("check")
        req = p.parse_request(env)
        receipts = []
        class FakePlay:
            def __init__(self, token, package):
                pass

            def start(self):
                raise p.PlayHttpStop(403, "PERMISSION_DENIED")

        with patch.object(p, "Play", FakePlay), patch.object(
            p, "persist", side_effect=lambda receipt: receipts.append(dict(receipt))
        ):
            with self.assertRaises(p.Stop):
                p.execute("check", req, "fixture-credential", env)
        self.assertEqual(receipts[0]["status"], "check_not_verified")
        self.assertEqual(receipts[0]["failedOperation"], "edit_create")
        self.assertEqual(receipts[0]["deployment"], "not_committed")
        workflow = (ROOT / ".github/workflows/play-closed-release.yml").read_text(encoding="utf-8")
        self.assertIn("Save sanitized read-only inspection result\n        if: always()", workflow)


if __name__ == "__main__":
    unittest.main()
