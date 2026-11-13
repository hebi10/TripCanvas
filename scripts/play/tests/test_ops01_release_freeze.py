"""OPS-01: keep Play publishing manual-only while retaining automatic Android CI."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
WORKFLOWS = ROOT / ".github" / "workflows"


class ManualPlayFreezeTests(unittest.TestCase):
    def test_release_workflows_are_manual_and_approved(self):
        for name in ("play-internal.yml", "play-promote.yml"):
            with self.subTest(workflow=name):
                text = (WORKFLOWS / name).read_text(encoding="utf-8")
                self.assertRegex(text, r"(?m)^  workflow_dispatch:")
                self.assertNotRegex(text, r"(?m)^  (?:push|workflow_run|issue_comment|schedule):")
                self.assertIn("final_release_confirmed", text)


if __name__ == "__main__":
    unittest.main()
