import importlib.util
import pathlib
import unittest

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

    def test_deploy_requires_both_completed_verify_jobs_on_exact_sha(self):
        run = {'head_sha': SHA, 'head_branch': 'main', 'event': 'push', 'conclusion': 'success', 'path': '.github/workflows/android-ci.yml'}
        jobs = [{'name': 'android-build', 'conclusion': 'success'}, {'name': 'room-migration-instrumentation', 'conclusion': 'success'}]
        source.verify_ci(SHA, run, jobs)
        for invalid_jobs in ([], jobs[:1], jobs[1:], [jobs[0], dict(jobs[1], conclusion='skipped')], [dict(jobs[0], conclusion='failure'), jobs[1]]):
            with self.assertRaises(source.SourceError):
                source.verify_ci(SHA, run, invalid_jobs)
        with self.assertRaises(source.SourceError):
            source.verify_ci('b' * 40, run, jobs)


if __name__ == '__main__':
    unittest.main()
