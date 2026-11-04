"""Failure evidence must survive CI cleanup without disclosing its database secret."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('start_sonar', Path(__file__).with_name('start_sonar.py'))
startup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(startup)


class StartSonarTests(unittest.TestCase):
    def run_start(self, execute, password='fixture-private-password'):
        with tempfile.TemporaryDirectory() as folder:
            report = Path(folder) / 'quality' / 'startup.log'
            output, errors = io.StringIO(), io.StringIO()
            with patch.dict(os.environ, {'SONAR_CI_DB_PASSWORD': password}), \
                 patch.object(startup, 'execute', side_effect=execute) as command, \
                 contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
                code = startup.start(report)
            contents = report.read_text() if report.exists() else ''
            return code, contents, output.getvalue(), errors.getvalue(), command.call_args_list

    def test_success_does_not_run_failure_diagnostics(self):
        code, report, output, _, calls = self.run_start(
            lambda command, timeout: subprocess.CompletedProcess(command, 0, 'Healthy', ''))
        self.assertEqual(code, 0)
        self.assertIn('Healthy', report)
        self.assertIn('Healthy', output)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0].args, (startup.COMPOSE + ['up', '-d', '--wait', '--wait-timeout', '900'], 930))

    def test_failed_start_preserves_safe_evidence_and_original_exit_code(self):
        def execute(command, timeout):
            if 'up' in command:
                return subprocess.CompletedProcess(command, 7, 'Startup fixture-private-password', '')
            if command[-3:] == ['ps', '--all', '--quiet']:
                return subprocess.CompletedProcess(command, 0, 'abc123\n--privileged\nnot-a-docker-id\n', '')
            return subprocess.CompletedProcess(command, 0, 'fixture-private-password', 'diagnostic stderr')
        code, report, output, _, calls = self.run_start(execute)
        self.assertEqual(code, 7)
        for label in ['Compose status', 'SonarQube and database logs', 'Container state', 'Host memory', 'Docker disk usage']:
            self.assertIn(label, report)
        for text in [report, output]:
            self.assertNotIn('fixture-private-password', text)
            self.assertIn('[REDACTED]', text)
        inspections = [call.args[0] for call in calls if 'inspect' in call.args[0]]
        self.assertEqual(len(inspections), 1)
        self.assertEqual(inspections[0][-1], 'abc123')
        self.assertTrue(all('down' not in call.args[0] for call in calls))

    def test_diagnostic_timeouts_do_not_replace_startup_failure(self):
        def execute(command, timeout):
            if 'up' in command:
                return subprocess.CompletedProcess(command, 9, '', 'startup failed')
            raise subprocess.TimeoutExpired(command, timeout, output='fixture-private-password')
        code, report, output, _, _ = self.run_start(execute)
        self.assertEqual(code, 9)
        self.assertIn('timed out', report)
        self.assertNotIn('fixture-private-password', report + output)

    def test_startup_timeout_keeps_failure_and_captures_diagnostics(self):
        def execute(command, timeout):
            if 'up' in command:
                raise subprocess.TimeoutExpired(command, timeout)
            return subprocess.CompletedProcess(command, 0, '', '')
        code, report, _, _, _ = self.run_start(execute)
        self.assertEqual(code, 124)
        self.assertIn('Compose status', report)

    def test_missing_docker_is_reported_without_losing_failure(self):
        def execute(command, timeout):
            raise FileNotFoundError('fixture-private-password')
        code, report, output, _, _ = self.run_start(execute)
        self.assertEqual(code, 127)
        self.assertIn('Unable to execute Docker Compose', report)
        self.assertNotIn('fixture-private-password', report + output)

    def test_missing_password_never_starts_docker(self):
        code, report, _, errors, calls = self.run_start(None, password='')
        self.assertEqual(code, 2)
        self.assertEqual(calls, [])
        self.assertEqual(report, '')
        self.assertIn('SONAR_CI_DB_PASSWORD is required', errors)


if __name__ == '__main__':
    unittest.main()
