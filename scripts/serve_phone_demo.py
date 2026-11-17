#!/usr/bin/env python3
"""Start a session-scoped Feather phone server; external forwarding is configured separately."""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import sys
import urllib.parse

repo = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--project', type=Path, default=repo / 'samples/whatsapp')
parser.add_argument('--apk', type=Path, default=repo / 'build/native/android/feather-dev-host-debug.apk')
parser.add_argument('--port', type=int, default=8080)
parser.add_argument('--public-url', help='Externally reachable HTTP(S) origin, configured separately')
options = parser.parse_args()
project = options.project.resolve()
if not options.apk.is_file(): parser.error('Build Feather Go first with scripts/build_android_sample.py --dev-host')
target = Path(os.environ.get('CARGO_TARGET_DIR', repo / 'cli/target'))
cli = target / 'debug/feather-cli'
if not cli.is_file():
    executable = shutil.which('feather-cli')
    if not executable: parser.error('Build the CLI first: cargo build --locked --manifest-path cli/Cargo.toml')
    cli = Path(executable)
if options.public_url:
    parsed = urllib.parse.urlsplit(options.public_url)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
        parser.error('--public-url must be an HTTP(S) origin without credentials, path, query, or fragment')
    os.environ['FEATHER_PUBLIC_URL'] = options.public_url.rstrip('/')
session = project / '.feather/phone-session.json'
session.parent.mkdir(parents=True, exist_ok=True)
if session.exists():
    token = json.loads(session.read_text())['token']
else:
    token = secrets.token_urlsafe(32)
    with open(session, 'x', opener=lambda path, flags: os.open(path, flags, 0o600)) as output:
        json.dump({'token': token}, output)
os.environ['FEATHER_SESSION_TOKEN'] = token
os.environ['FEATHER_CLIENT_APK'] = str(options.apk.resolve())
base = os.environ.get('FEATHER_PUBLIC_URL', f'http://127.0.0.1:{options.port}').rstrip('/')
print(f'Connection page: {base}/session/{token}/', flush=True)
if 'FEATHER_PUBLIC_URL' not in os.environ:
    print('Local server only. Use the computer LAN address or configure an approved public forwarding URL for a remote phone.', flush=True)
os.execv(str(cli), [str(cli), 'start', '--project', str(project), '--host', '0.0.0.0', '--port', str(options.port)])
