from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / '.github/scripts/run-android-instrumentation.sh'
SCREEN_SCRIPT = SCRIPT.with_name('run-2c-screen-qa.sh')


class InstrumentationDiagnosticsTests(unittest.TestCase):
    def execute(self, gradle_status, adb_status, screen_qa=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            binaries = root / 'bin'
            binaries.mkdir()
            gradle = root / 'gradlew'
            gradle.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$*"\nexit ' + str(gradle_status) + '\n')
            gradle.chmod(0o755)
            adb = binaries / 'adb'
            adb.write_text(
                '#!/usr/bin/env bash\nprintf "%s\\n" "$*" >> "$FAKE_ADB_LOG"\n'
                'case "$*" in "shell wm "*|"shell settings "*) exit 0;; esac\n'
                'exit ' + str(adb_status) + '\n'
            )
            adb.chmod(0o755)
            report = root / 'app/build/outputs/androidTest-results/connected/TEST-fixture.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite tests="6" failures="0"/>')
            env = dict(os.environ, PATH=str(binaries) + os.pathsep + os.environ['PATH'], FAKE_ADB_LOG=str(root / 'adb-commands.txt'))
            if screen_qa:
                scripts = root / '.github/scripts'
                scripts.mkdir(parents=True)
                shutil.copy2(SCRIPT, scripts / SCRIPT.name)
                command = ['bash', str(SCREEN_SCRIPT), '1080x2400', '420']
            else:
                command = ['bash', str(SCRIPT)]
            result = subprocess.run(command, cwd=root, env=env, capture_output=True, text=True)
            evidence = root / ('qa-output' if screen_qa else 'qa-output/instrumentation')
            return result, (evidence / 'gradle-connected.txt').read_text(), (evidence / 'androidTest-results/connected/TEST-fixture.xml').exists(), (root / 'adb-commands.txt').read_text() if (root / 'adb-commands.txt').exists() else ''

    def test_collection_runs_after_success_and_preserves_gradle_success(self):
        result, log, report, adb = self.execute(0, 0)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(log)
        self.assertTrue(report)
        self.assertIn('logcat', adb)
        self.assertIn('/files/qa/', adb)
        self.assertIn('/files/qa3a/', adb)
        self.assertIn('run-as com.hebi.tripcanvas tar -cf - databases files/datastore', adb)
        self.assertNotIn('testInstrumentationRunnerArguments.class', log)

    def test_failed_gradle_exit_is_preserved_even_when_all_adb_collection_commands_fail(self):
        result, log, report, adb = self.execute(37, 9)
        self.assertEqual(result.returncode, 37, result.stderr)
        self.assertTrue(log)
        self.assertTrue(report)
        self.assertIn('dumpsys activity', adb)
        self.assertIn('dumpsys window', adb)
        self.assertIn('uiautomator dump', adb)

    def test_phone_tablet_runner_preserves_failure_and_original_junit(self):
        result, log, report, adb = self.execute(37, 9, screen_qa=True)
        self.assertEqual(result.returncode, 37, result.stderr)
        self.assertTrue(report)
        self.assertIn('testInstrumentationRunnerArguments.class=com.hebi.tripcanvas.qa.TripCanvas2CScreenshotTest', log)
        self.assertIn('logcat', adb)
        self.assertIn('databases files/datastore', adb)
