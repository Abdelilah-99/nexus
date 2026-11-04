#!/usr/bin/env python3
"""Start disposable CI SonarQube and preserve safe evidence before cleanup."""
import os
from pathlib import Path
import subprocess
import sys

REPORT = Path('reports/quality/sonar-startup.log')
COMPOSE = ['docker', 'compose', '-p', 'buy01-quality', '-f', '.ci/compose.sonar.yml']


def execute(command, timeout):
    return subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=False)


def start(report=REPORT):
    password = os.environ.get('SONAR_CI_DB_PASSWORD', '')
    if not password:
        print('SONAR_CI_DB_PASSWORD is required', file=sys.stderr)
        return 2
    entries = []

    def record(label, value):
        safe = str(value).replace(password, '[REDACTED]')
        entries.append(f'## {label}\n{safe}\n')
        print(entries[-1], flush=True)

    def collect(label, command):
        try:
            result = execute(command, 30)
            record(label, result.stdout + result.stderr)
            return result
        except (OSError, subprocess.TimeoutExpired):
            record(label, 'Diagnostic command unavailable or timed out.')
            return None

    try:
        result = execute(COMPOSE + ['up', '-d', '--wait', '--wait-timeout', '900'], 930)
        record('Compose startup', result.stdout + result.stderr)
        code = result.returncode
    except subprocess.TimeoutExpired:
        record('Compose startup', 'Startup command timed out after 930 seconds.')
        code = 124
    except OSError:
        record('Compose startup', 'Unable to execute Docker Compose.')
        code = 127

    if code:
        record('Startup failure', f'Exit code: {code}. Diagnostics captured before container cleanup.')
        collect('Compose status', COMPOSE + ['ps', '--all'])
        collect('SonarQube and database logs', COMPOSE + ['logs', '--no-color', '--timestamps', '--tail', '200'])
        containers = collect('Container IDs', COMPOSE + ['ps', '--all', '--quiet'])
        if containers is not None and containers.returncode == 0:
            for container in containers.stdout.splitlines():
                container = container.strip()
                # Docker IDs only; never interpret output as command options or shell code.
                if container and all(c in '0123456789abcdef' for c in container):
                    collect('Container state', ['docker', 'inspect', '--format',
                            '{{.Name}} {{json .State}}', container])
        collect('Host memory', ['free', '-m'])
        collect('Docker disk usage', ['docker', 'system', 'df'])

    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text('\n'.join(entries))
    return code


if __name__ == '__main__':
    sys.exit(start())
