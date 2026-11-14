"""Offline OPS-07 automatic-event and release authorization negative fixtures."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("ops07audit", ROOT / "scripts/ops07/audit.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def module(path):
    full = ROOT / path
    if not full.is_file():
        return None
    spec = importlib.util.spec_from_file_location("ops07_" + full.stem, full)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


class NegativeScenarios(unittest.TestCase):
    def test_auto_publisher_is_detected(self):
        for event in ("push","workflow_run","issue_comment"):
            with self.subTest(event=event):
                example = ("on:\n  "+event+":\n    types: [completed]\n"
                           "jobs:\n  publish:\n    if: needs.gate.outputs.ready == 'true'\n"
                           "    runs-on: ubuntu-latest\n    steps:\n      - run: python3 publisher.py deploy\n")
                self.assertTrue(audit.automatic_mutations("play-release.yml", example))

    def test_manual_or_disabled_publisher_is_allowed(self):
        example = ("on:\n  workflow_run:\n    types: [completed]\n  workflow_dispatch:\n"
                   "jobs:\n  publish:\n    if: github.event_name == 'workflow_dispatch' && inputs.approved == true\n"
                   "    runs-on: ubuntu-latest\n    steps:\n      - run: python3 publisher.py deploy\n"
                   "  release:\n    if: >-\n      false && github.event_name == 'workflow_run'\n"
                   "    runs-on: ubuntu-latest\n    steps:\n      - run: gh release create v1\n")
        self.assertEqual(audit.automatic_mutations("play-release.yml", example), [])

    def test_manual_or_auto_condition_does_not_count_as_approval(self):
        example = ("on:\n  workflow_run:\n    types: [completed]\n  workflow_dispatch:\n"
                   "jobs:\n  upload:\n    if: >-\n"
                   "      github.event_name == 'workflow_dispatch' ||\n"
                   "      github.event_name == 'workflow_run'\n"
                   "    runs-on: ubuntu-latest\n    steps:\n      - run: python3 listing.py --apply\n")
        self.assertTrue(audit.automatic_mutations("store-listing.yml", example))


class AppContracts(unittest.TestCase):
    def test_all_release_stages_and_no_automatic_publishers(self):
        problems = audit.findings()
        self.assertEqual(problems, [], "\n".join(problems))

    def test_mismatched_source_and_fake_store_approval(self):
        mod = module("scripts/ops06/store_gate.py")
        self.assertIsNotNone(mod, "OPS-06 store guard is not present")
        env = {"GITHUB_REPOSITORY": audit.REPOSITORY, "GITHUB_EVENT_NAME": "workflow_dispatch",
               "GITHUB_REF": "refs/heads/main", "GITHUB_SHA": "a"*40,
               "OPS06_EXPECTED_MAIN_SHA": "a"*40, "OPS06_STORE_APPROVED": "true",
               "OPS06_CONFIRMATION": mod.PHRASE,
               "OPS06_BATCH_ID": "2026-11-13-"+mod.APP_SLUG}
        for key, value in (("GITHUB_EVENT_NAME", "workflow_run"),
                           ("GITHUB_REF", "refs/heads/feature"),
                           ("OPS06_STORE_APPROVED", "false"),
                           ("OPS06_CONFIRMATION", "WRONG"),
                           ("OPS06_EXPECTED_MAIN_SHA", "b"*40)):
            with self.subTest(key=key):
                with patch.object(mod, "command_output") as network:
                    with self.assertRaises(mod.StoreApprovalError):
                        mod.verify({**env,key:value})
                    network.assert_not_called()

    def test_unapproved_closed_track_wrong_bundle_and_draft(self):
        mod = module("scripts/ops05/promotion.py")
        self.assertIsNotNone(mod, "OPS-05 closed promotion source missing")
        env = {"GITHUB_EVENT_NAME":"workflow_dispatch","GITHUB_REF":"refs/heads/main",
               "GITHUB_REPOSITORY":audit.REPOSITORY,"EXPECTED_REPO":audit.REPOSITORY,
               "GITHUB_SHA":"a"*40,"EXPECTED_MAIN_SHA":"a"*40,
               "BATCH_ID":"2026-11-13-ops07","APP_PACKAGE":audit.PACKAGE,
               "INTERNAL_TRACK":"internal","CLOSED_TRACK":"alpha",
               "VERSION_CODE":"19","AAB_SHA256":"b"*64,"MODE":"promote",
               "FINAL_BATCH_APPROVED":"true","APPROVAL_PHRASE":mod.APPROVAL,
               "HOMECREW_FINAL_APPROVAL":"HOMECREW_FINAL_RELEASE_APPROVED"}
        for key, value in (("FINAL_BATCH_APPROVED","false"),
                           ("AAB_SHA256","z"*64),
                           ("CLOSED_TRACK","production"),
                           ("CLOSED_TRACK","internal"),
                           ("EXPECTED_MAIN_SHA","c"*40)):
            with self.subTest(key=key,value=value):
                with self.assertRaises(mod.Stop):
                    mod.parse_request({**env,key:value})
        req=mod.parse_request(env)
        tracks=[{"track":"internal","releases":[
            {"status":"completed","versionCodes":["19"],"name":"1.0"}]},
            {"track":"alpha","releases":[]}]
        self.assertEqual(mod.make_plan(tracks,[{"versionCode":19,"sha256":"b"*64}],req)["state"],"ready")
        with self.assertRaises(mod.Stop):
            mod.make_plan(tracks,[{"versionCode":19,"sha256":"c"*64}],req)
        tracks[0]["releases"][0]["status"]="draft"
        with self.assertRaises(mod.Stop):
            mod.make_plan(tracks,[{"versionCode":19,"sha256":"b"*64}],req)

    def test_existing_aab_rejects_mismatched_approved_digest(self):
        mod = module("scripts/ops04/internal.py")
        self.assertIsNotNone(mod, "OPS-04 immutable AAB guard missing")
        with tempfile.TemporaryDirectory() as tmp:
            aab = Path(tmp) / "signed-source.aab"
            aab.write_bytes(b"offline-aab-digest-mismatch-fixture")
            with patch.object(mod, "BUNDLE_PATH", Path(tmp)), \
                 patch.dict(mod.os.environ, {"AAB_SHA256": "0" * 64}), \
                 patch.object(mod, "invoke") as tool:
                with self.assertRaisesRegex(
                        mod.GateError, "Downloaded AAB SHA-256 differs from approved bytes"):
                    mod.verify()
                tool.assert_not_called()

    def test_build_request_approval_and_source_cannot_be_self_authorized(self):
        source_path = ".github/workflows/release-build.yml"
        source = audit.read(source_path)
        read_file = audit.read
        variants = (
            ("FINAL_BATCH_APPROVED", "final_batch_approved", "'true'"),
            ("EXPECTED_MAIN_SHA", "expected_main_sha", "${{ github.sha }}"),
        )
        for key, field, value in variants:
            with self.subTest(key=key):
                expected = key + ": " + "$" + "{{ inputs." + field + " }}"
                self.assertIn(expected, source)
                changed = source.replace(expected, key + ": " + value, 1)
                with patch.object(audit, "read", side_effect=lambda path:
                                  changed if path == source_path else read_file(path)):
                    blockers = audit.findings()
                self.assertTrue(any("APPROVAL_BINDING: OPS-03" in item
                                    for item in blockers), blockers)

    def test_ops03_preflight_cannot_be_skipped_or_ignored(self):
        source_path = ".github/workflows/release-build.yml"
        original_read = audit.read
        source = original_read(source_path)
        gate = "      - name: Gate release batch, main SHA and required CI\n"
        self.assertIn(gate, source)
        variants = (
            ("skipped", source.replace(gate, gate + "        if: false\n", 1)),
            ("failure_tolerant", source.replace(
                gate, gate + "        continue-on-error: true\n", 1)),
            ("removed", source.replace(
                gate, "      - name: Unrelated diagnostic\n", 1)),
        )
        for name, changed in variants:
            with self.subTest(name=name):
                with patch.object(audit, "read", side_effect=lambda path:
                                  changed if path == source_path else original_read(path)):
                    blockers = audit.findings()
                self.assertTrue(any("OPS03_PREFLIGHT:" in item for item in blockers),
                                blockers)

    def test_ops03_build_dispatch_cannot_run_after_failed_gate(self):
        path = ".github/workflows/release-build.yml"
        read_file = audit.read
        original = read_file(path)
        dispatch = "      - name: Dispatch existing signed AAB build-only mode\n"
        self.assertIn(dispatch, original)
        for condition in ("always()", "failure()", "cancelled()"):
            with self.subTest(condition=condition):
                altered = original.replace(dispatch, dispatch + "        if: " + condition + "\n", 1)
                with patch.object(audit, "read", side_effect=lambda name:
                                  altered if name == path else read_file(name)):
                    blockers = audit.findings()
                self.assertTrue(any("OPS03_DISPATCH_BYPASS:" in item for item in blockers),
                                blockers)

    def test_ops03_required_android_ci_cannot_be_disabled(self):
        path = ".github/workflows/release-build.yml"
        original_read = audit.read
        original = original_read(path)
        expected = """REQUIRED_CI_JSON: '["TripCanvas Android CI"]'"""
        self.assertIn(expected, original)
        for label, changed in (
            ("empty", original.replace(expected, "REQUIRED_CI_JSON: '[]'", 1)),
            ("wrong_ci", original.replace(expected, """REQUIRED_CI_JSON: '["Unknown"]'""", 1)),
            ("skip_missing", original.replace(
                'if not matching: refuse("Missing exact-main CI: "+name)',
                "if not matching: pass", 1)),
            ("skip_failed", original.replace(
                'if latest.get("status")!="completed" or latest.get("conclusion")!="success": refuse("Required CI not successful: "+name)',
                "if False: refuse('unreachable')", 1)),
        ):
            with self.subTest(label=label):
                with patch.object(audit, "read", side_effect=lambda filename:
                                  changed if filename == path else original_read(filename)):
                    issues = audit.findings()
                self.assertTrue(any("OPS03_REQUIRED_CI:" in item or
                                    "OPS03_PREFLIGHT:" in item for item in issues), issues)

    def test_ops03_step_ci_override_and_secondary_dispatch_are_blocked(self):
        path = ".github/workflows/release-build.yml"
        original_read = audit.read
        original = original_read(path)
        token = "          GH_TOKEN: " + "$" + "{{ github.token }}"
        self.assertIn(token, original)
        self.assertIn("python3 - <<'PY'", original)
        variants = (
            ("step_override", original.replace(
                token, token + "\n          REQUIRED_CI_JSON: '[]'", 1)),
            ("secondary_job", original + "\n  bypass:\n"
             "    runs-on: ubuntu-latest\n"
             "    steps:\n"
             "      - run: gh api --method POST repos/example/actions/workflows/play-internal.yml/dispatches\n"),
            ("masked_gate", original.replace("python3 - <<'PY'",
                                             "python3 - <<'PY' || true", 1)),
        )
        for label, changed in variants:
            with self.subTest(label=label):
                with patch.object(audit, "read", side_effect=lambda target:
                                  changed if target == path else original_read(target)):
                    issues = audit.findings()
                self.assertTrue(any(item.startswith(("OPS03_REQUIRED_CI:", "OPS03_SCOPE:",
                                                       "OPS03_PREFLIGHT:")) for item in issues), issues)

    def test_approved_ops03_values_must_be_in_request_job_env(self):
        source_path = ".github/workflows/release-build.yml"
        source = audit.read(source_path)
        expected = "FINAL_BATCH_APPROVED: " + "$" + "{{ inputs.final_batch_approved }}"
        self.assertIn(expected, source)
        changed = source.replace(expected, "FINAL_BATCH_APPROVED: 'true'", 1)
        changed = changed.replace(
            "      - name: Gate release batch, main SHA and required CI",
            "      - name: Unrelated diagnostic\n"
            "        env:\n"
            "          FINAL_BATCH_APPROVED: " + "$" + "{{ inputs.final_batch_approved }}\n"
            "        run: echo preflight\n"
            "      - name: Gate release batch, main SHA and required CI", 1)
        read_file = audit.read
        with patch.object(audit, "read", side_effect=lambda path:
                          changed if path == source_path else read_file(path)):
            blockers = audit.findings()
        self.assertTrue(any("APPROVAL_BINDING: OPS-03" in item
                            for item in blockers), blockers)

    def test_release_approval_inputs_cannot_be_fabricated(self):
        for path, stage, key, field, value in (
            (".github/workflows/play-internal-release.yml", "OPS-04",
             "FINAL_RELEASE_APPROVED", "final_release_approved", "'true'"),
            (".github/workflows/play-internal-release.yml", "OPS-04",
             "APPROVAL_PHRASE", "approval_phrase", "PUBLISH_VERIFIED_INTERNAL_AAB"),
            (".github/workflows/play-internal-release.yml", "OPS-04",
             "AAB_SHA256", "aab_sha256", "'" + "0" * 64 + "'"),
            (".github/workflows/play-internal-release.yml", "OPS-04",
             "EXPECTED_MAIN_SHA", "expected_main_sha", "${{ github.sha }}"),
            (".github/workflows/play-closed-release.yml", "OPS-05",
             "EXPECTED_MAIN_SHA", "expected_main_sha", "${{ github.sha }}"),
            (".github/workflows/play-closed-release.yml", "OPS-05",
             "CLOSED_TRACK", "closed_track", "alpha"),
            (".github/workflows/play-closed-release.yml", "OPS-05",
             "AAB_SHA256", "aab_sha256", "'" + "0" * 64 + "'"),
            (".github/workflows/play-closed-release.yml", "OPS-05",
             "FINAL_BATCH_APPROVED", "final_batch_approved", "'true'"),
            (".github/workflows/play-closed-release.yml", "OPS-05",
             "APPROVAL_PHRASE", "approval_phrase", "PROMOTE_VERIFIED_INTERNAL_VERSION"),
        ):
            with self.subTest(path=path, key=key):
                original = audit.read(path)
                expected = key + ": ${{ inputs." + field + " }}"
                self.assertIn(expected, original)
                altered = original.replace(expected, key + ": " + value)
                with patch.object(audit, "read", side_effect=lambda name:
                                  altered if name == path else (Path(audit.ROOT) / name).read_text(
                                      encoding="utf-8") if (Path(audit.ROOT) / name).is_file()
                                      else None):
                    issues = audit.findings()
                self.assertTrue(any("APPROVAL_BINDING: " + stage in issue
                                    for issue in issues), issues)

    def test_release_approval_cannot_be_overridden_in_job_environment(self):
        source = audit.read(".github/workflows/play-internal-release.yml")
        self.assertIn("          GH_TOKEN: ${{ github.token }}", source)
        altered = source.replace(
            "          GH_TOKEN: ${{ github.token }}",
            "          GH_TOKEN: ${{ github.token }}\n"
            "          FINAL_RELEASE_APPROVED: 'true'", 1)
        issues = audit._release_input_binding_findings(
            altered, (("FINAL_RELEASE_APPROVED", "final_release_approved"),), "OPS-04")
        self.assertTrue(any("APPROVAL_BINDING: OPS-04" in issue for issue in issues), issues)

    def test_missing_existing_aab_cannot_be_published(self):
        mod=module("scripts/ops04/internal.py")
        self.assertIsNotNone(mod, "OPS-04 immutable AAB guard missing")
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(mod,"BUNDLE_PATH",Path(tmp)):
                with self.assertRaises(mod.GateError):
                    mod.verify()


if __name__ == "__main__":
    unittest.main()
