"""Exercise entrypoint behavior with fixture files, without touching a Jenkins home."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import bootstrap

ROOT = Path(__file__).resolve().parents[1]

class ConfigurationTests(unittest.TestCase):
    def run_entrypoint(self, missing=None, public=False):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            secrets = root / 'secrets'
            secrets.mkdir()
            for name in ('github-username', 'github-pat', 'jenkins-admin-password', 'buy01-env',
                         'gateway-keystore.p12', 'gateway-keystore.crt', 'gateway-keystore.key'):
                (secrets / name).write_text('fixture')
            for component, service in bootstrap.COMPONENTS.items():
                (secrets / f'sonar-{component}-token').write_text('fixture-' + component)
                (secrets / f'sonar-{component}-project-key').write_text('buy01-' + service)
            if public:
                (secrets / 'github-pat').write_text('')
            if missing:
                (secrets / missing).unlink()
            entrypoint = (ROOT / 'docker-entrypoint.sh').read_text()
            output = root / 'base.yaml'
            entrypoint = entrypoint.replace('/tmp/jenkins-public.', str(root / 'public.')).replace('/run/secrets', str(secrets)).replace('/casc/jenkins.yaml', str(ROOT / 'jcasc/jenkins.yaml')).replace('/tmp/jenkins-base.yaml', str(output))
            # Replace only OS handoff tools in a private PATH, never start a controller.
            tools = root / 'bin'
            tools.mkdir()
            (tools / 'chown').write_text('#!/bin/sh\nexit 0\n')
            (tools / 'su').write_text('#!/usr/bin/env python3\nimport os,json\nprint(json.dumps({k:v for k,v in os.environ.items() if k.startswith("SONAR_") or k=="CASC_JENKINS_CONFIG"}))\n')
            for path in tools.iterdir():
                path.chmod(0o700)
            env = {k: v for k, v in os.environ.items() if not k.startswith('SONAR_')}
            env.update(PATH=str(tools) + os.pathsep + os.environ['PATH'], CASC_JENKINS_CONFIG=str(ROOT / 'jcasc/jenkins.yaml'))
            result = subprocess.run(['bash', '-c', entrypoint], env=env, text=True, capture_output=True, check=True)
            exported = json.loads(result.stdout)
            selected = Path(exported['CASC_JENKINS_CONFIG'])
            return exported, selected.read_text() if selected != ROOT / 'jcasc/jenkins.yaml' else None

    def test_all_seven_credentials_and_keys_are_exported(self):
        exported, fallback = self.run_entrypoint()
        self.assertIsNone(fallback)
        self.assertEqual(exported['SONAR_ORDER_PROJECT_KEY'], 'buy01-order-service')
        self.assertEqual(exported['SONAR_ORDER_TOKEN'], 'fixture-order')
        for component in bootstrap.COMPONENTS:
            self.assertIn(f'SONAR_{component.upper()}_TOKEN', exported)
            self.assertIn(f'SONAR_{component.upper()}_PROJECT_KEY', exported)

    def test_missing_order_token_or_key_keeps_optional_sonar_configuration(self):
        for missing in ('sonar-order-token', 'sonar-order-project-key'):
            with self.subTest(missing=missing):
                exported, fallback = self.run_entrypoint(missing)
                self.assertTrue(exported['CASC_JENKINS_CONFIG'].endswith('/base.yaml'))
                self.assertNotIn('${SONAR_', fallback)
                self.assertNotIn('sonar-product-token', fallback)
                self.assertIn('${JENKINS_ADMIN_PASSWORD}', fallback)
                self.assertIn('github-mr-jenk', fallback)

    def test_jcasc_pipeline_and_live_verifier_include_every_component(self):
        config = (ROOT / 'jcasc/jenkins.yaml').read_text()
        pipeline = (ROOT.parent / 'Jenkinsfile').read_text()
        verifier = (ROOT / 'sonar-init/verify-jenkins.js').read_text()
        for component, service in bootstrap.COMPONENTS.items():
            with self.subTest(component=component):
                self.assertIn(f'id: "sonar-{component}-token"', config)
                self.assertIn('${SONAR_' + component.upper() + '_TOKEN}', config)
                self.assertIn('${SONAR_' + component.upper() + '_PROJECT_KEY}', config)
                self.assertRegex(pipeline, re.escape(f"['{service}', '{component}', 'buy01-{service}']"))
                self.assertIn("'" + component + "'", verifier)
        self.assertIn('waitForQualityGate(credentialsId: credential, abortPipeline: true)', pipeline)
        self.assertIn('throw failure', pipeline)

    def test_order_build_deploy_coverage_and_archive_paths_are_preserved(self):
        pipeline = (ROOT.parent / 'Jenkinsfile').read_text()
        self.assertIn("dir('order-service')", pipeline)
        self.assertIn('media-service order-service gateway discovery;', pipeline)
        self.assertIn('docker image inspect order-service:${BUILD_NUMBER}', pipeline)
        self.assertIn('ps --all --quiet "$SERVICE"', pipeline)
        self.assertIn('check_container "$CONTAINER_ID" || return 1', pipeline)
        self.assertIn('media-mongodb user-mongodb order-mongodb', pipeline)
        self.assertIn('media-service order-service gateway frontend; do', pipeline)
        self.assertIn("'**/target/site/jacoco/**, frontend/coverage/frontend/**'", pipeline)
        workflow = (ROOT.parent / '.github/workflows/quality.yml').read_text()
        self.assertIn('media-service order-service gateway discovery;', workflow)
        self.assertIn('test -s "$service/target/site/jacoco/jacoco.xml"', workflow)
        self.assertIn('**/target/site/jacoco/**', workflow)
        self.assertIn('**/target/surefire-reports/*.xml', workflow)

    def test_public_checkout_omits_empty_github_credentials(self):
        for missing in (None, 'sonar-order-token'):
            with self.subTest(missing=missing):
                exported, selected = self.run_entrypoint(missing, public=True)
                self.assertNotIn('github-mr-jenk', selected)
                self.assertNotIn('${GITHUB_PAT}', selected)
                self.assertIn('${JENKINS_ADMIN_PASSWORD}', selected)
                if missing is None:
                    self.assertIn('${SONAR_ORDER_TOKEN}', selected)
                else:
                    self.assertNotIn('${SONAR_', selected)

class PullRequestValidationTests(unittest.TestCase):
    def run_validator(self, metadata, number='26'):
        with tempfile.TemporaryDirectory() as folder:
            script = ROOT / 'jcasc/validate-pr.mjs'
            code = 'globalThis.fetch = async () => ({ok:true,json:async()=>(' + json.dumps(metadata) + ')}); await import(' + json.dumps(script.as_uri()) + ');'
            result = subprocess.run(['node', '--input-type=module', '-e', code],
                                    cwd=folder, env=dict(os.environ, PR_NUMBER=number),
                                    text=True, capture_output=True)
            file = Path(folder) / 'pr-head.sha'
            return result.returncode, file.read_text() if file.exists() else None

    def metadata(self):
        return {'head': {'repo': {'full_name': 'AyyoubMh48/buy-02'}, 'sha': 'a' * 40},
                'base': {'ref': 'main'}, 'author_association': 'COLLABORATOR'}

    def test_trusted_team_pull_request_pins_exact_commit(self):
        status, sha = self.run_validator(self.metadata())
        self.assertEqual(status, 0)
        self.assertEqual(sha, 'a' * 40 + '\n')

    def test_forks_and_untrusted_authors_cannot_reach_build_steps(self):
        for field in ('fork', 'author'):
            metadata = self.metadata()
            if field == 'fork':
                metadata['head']['repo']['full_name'] = 'external/fork'
            else:
                metadata['author_association'] = 'NONE'
            with self.subTest(field=field):
                status, sha = self.run_validator(metadata)
                self.assertNotEqual(status, 0)
                self.assertIsNone(sha)

    def test_non_numeric_parameter_is_rejected(self):
        status, sha = self.run_validator(self.metadata(), '26; invalid')
        self.assertNotEqual(status, 0)
        self.assertIsNone(sha)


class DeploymentEnvironmentTests(unittest.TestCase):
    def test_example_storage_values_cannot_be_deployed(self):
        result = subprocess.run(['node', str(ROOT / 'check-deploy-env.mjs'), '.env.example'],
                                cwd=ROOT.parent, text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('CLOUDINARY_CLOUD_NAME', result.stderr)
        self.assertNotIn('CLOUDINARY_CLOUD_NAME=', result.stderr)

    def test_non_example_storage_values_are_accepted_without_printing_them(self):
        with tempfile.NamedTemporaryFile(mode='w') as file:
            file.write('CLOUDINARY_CLOUD_NAME=private-audit-cloud\nCLOUDINARY_API_KEY=private-audit-key\nCLOUDINARY_API_SECRET=private-audit-secret\n')
            file.flush()
            result = subprocess.run(['node', str(ROOT / 'check-deploy-env.mjs'), file.name],
                                    cwd=ROOT.parent, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0)
            self.assertNotIn('private-audit', result.stdout + result.stderr)
