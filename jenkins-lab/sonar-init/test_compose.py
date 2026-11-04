"""Validate resolved Compose mounts and isolation using synthetic credentials only."""
import json
import os
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]

class ComposeTests(unittest.TestCase):
    def config(self, overlay=None, **values):
        env = os.environ.copy()
        for key in re.findall(r'^([A-Z][A-Z_0-9]*)=', (ROOT / '.env.example').read_text(), re.M):
            env[key] = 'fixture'
        env.update(APP_IMAGE_TAG='fixture', FRONTEND_PORT='4200', GATEWAY_PORT='8444',
                   KAFKA_PUBLISH='9092', CORS_ALLOWED_ORIGINS='https://localhost:4200',
                   PRODUCT_MONGO_PORT='27019', MEDIA_MONGO_PORT='27020',
                   USER_MONGO_PORT='27017', ORDER_MONGO_PORT='27021')
        env.update(values)
        command = ['docker', 'compose', '--env-file', '.env.example', '-p', 'compose-fixture', '-f', 'docker-compose.yml']
        if overlay:
            command += ['-f', overlay]
        command += ['config', '--format', 'json']
        result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, text=True, check=True)
        return json.loads(result.stdout)

    def test_staging_has_no_controller_filesystem_binds_or_local_port_collisions(self):
        config = self.config('compose.jenkins-host.yml', DEPLOY_PROJECT='buy02-staging',
                DEPLOY_CERT_VOLUME='buy02-staging-certs', DEPLOY_MONGO_INIT_VOLUME='buy02-staging-mongo-init',
                FRONTEND_PORT='4300', GATEWAY_PORT='127.0.0.1:8445', KAFKA_PUBLISH='127.0.0.1:19092',
                PRODUCT_MONGO_PORT='37019', MEDIA_MONGO_PORT='37020', USER_MONGO_PORT='37017', ORDER_MONGO_PORT='37021',
                CORS_ALLOWED_ORIGINS='https://localhost:4300')
        ports = set()
        for name, service in config['services'].items():
            self.assertEqual(service['container_name'], 'buy02-staging-' + name)
            for mount in service.get('volumes', []):
                self.assertEqual(mount['type'], 'volume', (name, mount))
            for port in service.get('ports', []):
                ports.add(str(port['published']))
        self.assertEqual(ports, {'4300', '8445', '19092', '37019', '37020', '37017', '37021'})
        for name in ('gateway', 'frontend'):
            mounts = config['services'][name]['volumes']
            self.assertEqual(len(mounts), 1)
            self.assertTrue(mounts[0]['read_only'])
            self.assertEqual(mounts[0]['source'], 'staging_certs')
        for name in ('product', 'user', 'media', 'order'):
            mounts = config['services'][name + '-mongodb']['volumes']
            self.assertEqual(len(mounts), 2)
            init = next(m for m in mounts if m['target'] == '/docker-entrypoint-initdb.d')
            self.assertEqual(init['source'], 'staging_mongo_init')
            self.assertTrue(init['read_only'])
        self.assertEqual(config['services']['gateway']['environment']['CORS_ALLOWED_ORIGINS'], 'https://localhost:4300')

    def test_local_certificates_replace_default_jenkins_mounts(self):
        config = self.config('compose.local.yml')
        for name, target in (('gateway', '/run/secrets'), ('frontend', '/etc/nginx/certs')):
            mounts = config['services'][name]['volumes']
            self.assertEqual(len(mounts), 1)
            self.assertEqual(mounts[0]['target'], target)
            self.assertEqual(mounts[0]['source'], str(ROOT / '.local/certs'))
            self.assertTrue(mounts[0]['read_only'])
        self.assertEqual(str(config['services']['frontend']['ports'][0]['published']), '4200')
