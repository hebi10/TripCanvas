"""Offline QA for the Korean Google Play image bundle."""
import subprocess
import sys
import unittest
from pathlib import Path

class ListingAssetTest(unittest.TestCase):
    def test_store_assets(self):
        root = Path(__file__).resolve().parents[3]
        script = root / 'scripts' / 'play' / 'verify_listing_assets.py'
        result = subprocess.run([sys.executable, str(script)], cwd=root, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

if __name__ == '__main__':
    unittest.main()
