#!/usr/bin/env python3
"""Exercise QR/session routes and the chat demo through the actual native Kotlin bindings."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

repo = Path(__file__).resolve().parents[1]
target = Path(os.environ.get('CARGO_TARGET_DIR', repo / 'core/target')).resolve()
os.environ['CARGO_TARGET_DIR'] = str(target)
tools = Path(os.environ.get('FEATHER_TOOLS_HOME', Path.home() / '.cache/feather'))
cli = target / 'debug/feather-cli'
subprocess.run(['cargo', 'build', '--locked', '--manifest-path', repo / 'cli/Cargo.toml'], check=True)
subprocess.run(['cargo', 'build', '--locked', '--manifest-path', repo / 'core/Cargo.toml', '--lib'], check=True)
with tempfile.TemporaryDirectory(prefix='feather-phone-') as temp:
    work = Path(temp)
    project = work / 'whatsapp'
    shutil.copytree(repo / 'samples/whatsapp', project, ignore=shutil.ignore_patterns('.feather', '.featherbundle', 'dist'))
    token = secrets.token_urlsafe(32)
    apk = work / 'test-client.apk'
    apk.write_bytes(b'controlled download fixture')
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    env = os.environ.copy()
    env.update(FEATHER_SESSION_TOKEN=token, FEATHER_PUBLIC_URL='https://phone.example.test', FEATHER_CLIENT_APK=str(apk))
    base = f'http://127.0.0.1:{port}/session/{token}'
    def get(path):
        with urllib.request.urlopen(base + path, timeout=15) as response: return response.read()
    with (work / 'server.log').open('w+') as log:
        server = subprocess.Popen([cli, 'start', '--project', project, '--port', str(port)], env=env, stdout=log, stderr=log)
        try:
            for _ in range(1800):
                if server.poll() is not None:
                    log.seek(0)
                    raise AssertionError(log.read())
                if 'Watching for file changes' in (work / 'server.log').read_text(): break
                time.sleep(.1)
            else: raise AssertionError('Server startup timed out')
            for path in ['/', '/project', '/bundle/manifest', '/client.apk', '/devtools', '/ws', '/session/wrong/project']:
                try: urllib.request.urlopen(f'http://127.0.0.1:{port}' + path, timeout=5)
                except urllib.error.HTTPError as error: assert error.code == 404, (path, error.code)
                else: raise AssertionError('Unprotected route: ' + path)
            metadata = json.loads(get('/project'))
            params = urllib.parse.parse_qs(urllib.parse.urlsplit(metadata['connection_uri']).query)
            assert params['url'] == ['https://phone.example.test/session/' + token]
            assert params['key'] == [(project / '.feather/public_key.txt').read_text().strip()]
            assert b'<svg' in get('/qr.svg') and b'Feather Go' in get('/')
            assert get('/client.apk') == apk.read_bytes()
            manifest = json.loads(get('/bundle/manifest'))
            code = get('/bundle/artifact/app.js')
            assert hashlib.sha256(code).hexdigest() == manifest['javascript']['sha256']
            jna = tools / 'jna/jna.jar'
            assert hashlib.sha256(jna.read_bytes()).hexdigest() == '4fb141dd8ef6b0585ffceea4bc49602fbc6312fa977e2c488794ea3e6aafecae'
            kotlin = tools / 'kotlin/kotlinc'
            jar = work / 'phone-smoke.jar'
            subprocess.run(['java', '-Xmx1g', '-cp', str(kotlin / 'lib/*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                '-kotlin-home', kotlin, '-classpath', jna, '-jvm-target', '17', '-include-runtime', '-d', jar,
                repo / 'host/android/app/src/main/java/uniffi/feather_core/feather_core.kt', repo / 'scripts/fixtures/NativePhoneSmoke.kt'], check=True)
            subprocess.run(['java', '-Djna.library.path=' + str(target / 'debug'), '-Djna.tmpdir=' + str(work),
                '-cp', os.pathsep.join([str(jar), str(jna)]), 'NativePhoneSmokeKt', base,
                project / '.feather/public_key.txt', project / 'App.kt'], check=True)
            print('PASS: session-scoped portal/APK, QR project identity, signed download, and native chat/live-refresh smoke')
        finally:
            server.terminate()
            try: server.wait(timeout=5)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=5)
