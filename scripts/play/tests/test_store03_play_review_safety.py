"""STORE-03: Play releases must not cancel an existing review."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'publisher.py'
spec = importlib.util.spec_from_file_location('store03_publisher', SCRIPT)
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PlayReviewSafetyTests(unittest.TestCase):
    def test_normal_internal_and_closed_commit_are_fail_closed(self):
        for pending in (False, True):
            calls = []
            def request(method, path, payload=None, content_type=None):
                calls.append((method, path))
                return {'id': '123'}
            publisher.Publisher(request).commit('/edits/123', changes_not_sent_for_review=pending)
            commits = [path for method, path in calls if ':commit' in path]
            self.assertEqual(len(commits), 1)
            self.assertIn('changesInReviewBehavior=ERROR_IF_IN_REVIEW', commits[0])
            self.assertEqual('changesNotSentForReview=true' in commits[0], pending)
            self.assertFalse(any('CANCEL_IN_REVIEW_AND_SUBMIT' in x for x in commits))

    def test_review_hold_http400_is_not_retried_with_review_cancel(self):
        calls = []
        def request(method, path, payload=None, content_type=None):
            calls.append((method, path))
            if ':commit' in path:
                raise publisher.PlayApiError(method, 400, 'FAILED_PRECONDITION')
            return {}
        with self.assertRaises(publisher.PlayApiError):
            publisher.Publisher(request).commit('/edits/123')
        self.assertEqual(sum(':commit' in path for _, path in calls), 1)


if __name__ == '__main__':
    unittest.main()
