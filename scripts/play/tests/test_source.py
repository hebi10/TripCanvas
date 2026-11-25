import importlib.util
import pathlib
import unittest
from unittest.mock import patch

SCRIPT = pathlib.Path(__file__).parents[1] / 'source.py'
spec = importlib.util.spec_from_file_location('source', SCRIPT)
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)
SHA = 'a' * 40
REPO = 'hebi10/TripCanvas'


class SourceTests(unittest.TestCase):
    def test_release_modes_require_manual_final_confirmation(self):
        manual = {'GITHUB_REPOSITORY': REPO, 'GITHUB_EVENT_NAME': 'workflow_dispatch',
                  'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': SHA}
        self.assertEqual(source.release_mode(dict(manual, PLAY_REQUESTED_MODE='check'), {}, ''), 'check')
        self.assertEqual(source.release_mode(dict(manual, PLAY_REQUESTED_MODE='build'), {}, ''), 'build')
        for mode in ('deploy', 'initialize'):
            with self.subTest(mode=mode):
                with self.assertRaisesRegex(source.SourceError, 'Final release frozen'):
                    source.release_mode(dict(manual, PLAY_REQUESTED_MODE=mode), {}, '')
                self.assertEqual(
                    source.release_mode(dict(manual, PLAY_REQUESTED_MODE=mode,
                                             PLAY_FINAL_RELEASE_APPROVED='true'), {}, ''),
                    mode
                )
        for invalid in ('invalid', '', 'Deploy'):
            with self.assertRaises(source.SourceError):
                source.release_mode(dict(manual, PLAY_REQUESTED_MODE=invalid), {}, '')
        for ref in ('refs/tags/v1', 'refs/heads/feature'):
            with self.assertRaises(source.SourceError):
                source.release_mode(dict(manual, GITHUB_REF=ref,
                                         PLAY_REQUESTED_MODE='deploy',
                                         PLAY_FINAL_RELEASE_APPROVED='true'), {}, '')

    def test_manual_dispatch_only_accepts_main(self):
        for ref in ('refs/heads/feature', 'refs/tags/v1'):
            with self.assertRaises(source.SourceError):
                source.resolve({'GITHUB_REPOSITORY': REPO, 'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_REF': ref, 'GITHUB_SHA': SHA}, {})
        self.assertEqual(source.resolve({'GITHUB_REPOSITORY': REPO, 'GITHUB_EVENT_NAME': 'workflow_dispatch', 'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': SHA}, {}), SHA)

    def test_automatic_source_is_frozen_even_for_trusted_main_ci(self):
        run = {'name': 'TripCanvas Android CI', 'path': '.github/workflows/android-ci.yml',
               'event': 'push', 'conclusion': 'success', 'head_branch': 'main',
               'head_sha': SHA, 'head_repository': {'full_name': REPO}}
        env = {'GITHUB_REPOSITORY': REPO, 'GITHUB_EVENT_NAME': 'workflow_run'}
        with self.assertRaisesRegex(source.SourceError, 'Automatic Play upload is frozen'):
            source.resolve(env, {'workflow_run': run})
        with self.assertRaises(source.SourceError):
            source.release_mode(env, {'workflow_run': run}, '[initialize-play]')
        for invalid in ({}, {'workflow_run': {}}, {'workflow_run': dict(run, head_branch='feature')}):
            with self.assertRaises(source.SourceError):
                source.resolve(env, invalid)

    def test_stale_main_source_fails_before_deployment(self):
        source.require_current_main(SHA, SHA)
        with self.assertRaisesRegex(source.SourceError, 'stale'):
            source.require_current_main(SHA, 'b' * 40)

    def test_basic_android_build_passes_without_duplicate_instrumentation(self):
        run = {'head_sha': SHA, 'head_branch': 'main', 'event': 'push', 'status': 'completed',
               'conclusion': 'success', 'path': '.github/workflows/android-ci.yml'}
        jobs = [{'name': 'android-build', 'conclusion': 'success'},
                {'name': 'room-migration-instrumentation', 'conclusion': 'skipped'}]
        source.verify_ci(SHA, run, jobs)
        with self.assertRaisesRegex(source.SourceError, 'android-build'):
            source.verify_ci(SHA, run, jobs[1:])

    def test_final_qa_requires_exact_manual_run_and_successful_device_jobs(self):
        run = {'head_sha': SHA, 'head_branch': 'main', 'event': 'workflow_dispatch',
               'status': 'completed', 'conclusion': 'success', 'path': '.github/workflows/final-device-qa.yml'}
        jobs = [{'name': name, 'conclusion': 'success'}
                for name in source.WORKFLOW_REQUIREMENTS[source.FINAL_QA][1]]
        self.assertIn('room-migration / room-migration-instrumentation', {job['name'] for job in jobs})
        source.verify_ci(SHA, run, jobs)
        for wrong in (dict(run, head_sha='b' * 40), dict(run, head_branch='qa'), dict(run, event='push'),
                      dict(run, status='in_progress'), dict(run, conclusion='failure'),
                      dict(run, path='qa/final-device-qa.yml')):
            with self.subTest(wrong=wrong), self.assertRaises(source.SourceError):
                source.verify_ci(SHA, wrong, jobs)
        for job in jobs:
            with self.subTest(job=job['name']), self.assertRaisesRegex(source.SourceError, 'missing successful jobs'):
                source.verify_ci(SHA, run, [dict(job, conclusion='skipped') if item == job else item for item in jobs])

    def test_latest_final_run_failure_cannot_fall_back_to_an_older_success(self):
        run = {'id': 10, 'head_sha': SHA, 'head_branch': 'main', 'event': 'workflow_dispatch',
               'status': 'completed', 'conclusion': 'success', 'path': '.github/workflows/final-device-qa.yml'}
        jobs = [{'name': name, 'conclusion': 'success'}
                for name in source.WORKFLOW_REQUIREMENTS[source.FINAL_QA][1]]
        with patch.object(source, 'gh', side_effect=[{'workflow_runs': [run]}, {'jobs': jobs}]):
            self.assertEqual(source.verify_main_workflow(SHA, source.FINAL_QA), 10)
        with patch.object(source, 'gh', side_effect=[
                {'workflow_runs': [run, dict(run, id=11, conclusion='failure')]}, {'jobs': jobs}]):
            with self.assertRaisesRegex(source.SourceError, 'has not succeeded'):
                source.verify_main_workflow(SHA, source.FINAL_QA)


if __name__ == '__main__':
    unittest.main()
