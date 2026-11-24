"""Release freeze regression gates: no Play writes from code merges."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]


def events_section(filename):
    content = (ROOT / ".github" / "workflows" / filename).read_text(encoding="utf-8")
    return content.split("\non:\n", 1)[1].split("\npermissions:\n", 1)[0]


class ReleaseFreezeWorkflowTests(unittest.TestCase):
    def test_internal_aab_has_no_main_push_or_workflow_run_trigger(self):
        trigger = events_section("play-internal.yml")
        self.assertIn("  workflow_dispatch:", trigger)
        self.assertNotIn("  workflow_run:", trigger)
        self.assertNotIn("  push:", trigger)
        script = (ROOT / ".github/workflows/play-internal.yml").read_text(encoding="utf-8")
        self.assertIn("PLAY_FINAL_RELEASE_APPROVED:", script)
        self.assertIn("final_release_confirmed:", script)

    def test_store_assets_have_manual_upload_only(self):
        trigger = events_section("play-listing-images.yml")
        self.assertIn("  pull_request:", trigger)
        self.assertIn("  workflow_dispatch:", trigger)
        self.assertNotIn("  push:", trigger)
        wf = (ROOT / ".github/workflows/play-listing-images.yml").read_text(encoding="utf-8")
        self.assertIn("inputs.mode == 'upload' && inputs.final_release_confirmed == true", wf)
        self.assertIn("if: false # Historical one-time path frozen", wf)

    def test_closed_alpha_is_only_manual_explicit_final_approval(self):
        trigger = events_section("play-promote.yml")
        self.assertIn("  workflow_dispatch:", trigger)
        self.assertNotIn("  push:", trigger)
        self.assertNotIn("  workflow_run:", trigger)
        wf = (ROOT / ".github/workflows/play-promote.yml").read_text(encoding="utf-8")
        self.assertIn("inputs.mode == 'promote' && inputs.final_release_confirmed == true", wf)

    def test_source_rejects_any_automatic_play_release(self):
        source = (ROOT / "scripts/play/source.py").read_text(encoding="utf-8")
        self.assertIn("Automatic Play upload is frozen", source)
        self.assertIn("PLAY_FINAL_RELEASE_APPROVED", source)


if __name__ == "__main__":
    unittest.main()
