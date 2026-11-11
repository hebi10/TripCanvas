"""Ensure image-only merge commits do not allocate a new Android versionCode."""
import importlib.util
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'source.py'
spec = importlib.util.spec_from_file_location('tripcanvas_source', SOURCE)
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)


class ListingReleaseGuardTest(unittest.TestCase):
    def test_listing_only_changes_skip_aab(self):
        files = [
            'assets/play-store/ko-KR/01-home.png',
            'assets/play-store/ko-KR/manifest.json',
            'scripts/play/listing_images.py',
            '.github/workflows/play-listing-images.yml',
            'docs/play-store/store-02-listing.md',
        ]
        self.assertTrue(source.is_store_listing_only(files))

    def test_real_app_changes_still_require_build(self):
        for path in (
            'app/src/main/AndroidManifest.xml',
            'app/src/main/res/drawable-nodpi/ic_tripcanvas_launcher_foreground.png',
            '.github/workflows/play-internal.yml',
            'scripts/play/source.py',
            'app/build.gradle.kts',
        ):
            self.assertFalse(source.is_store_listing_only(['assets/play-store/ko-KR/01-home.png', path]))
        self.assertFalse(source.is_store_listing_only([]))
        self.assertFalse(source.is_store_listing_only(['.github/workflows/play-internal.yml']))


if __name__ == '__main__':
    unittest.main()
