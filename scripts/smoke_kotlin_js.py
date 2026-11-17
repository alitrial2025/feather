#!/usr/bin/env python3
"""Compile actual Kotlin and prove logic changes refresh without a native build."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
sys.dont_write_bytecode = True
from ws_test_client import WebSocket

repo = Path(__file__).resolve().parents[1]
binary = Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory(prefix="feather-kotlin-") as temp:
    project = Path(temp) / "counter"
    shutil.copytree(repo / "samples/runtime-counter", project, ignore=shutil.ignore_patterns(".feather", ".featherbundle", "dist"))
    subprocess.run([binary, "compile-js", "--project", project], check=True)
    original = (project / "App.kt").read_text()
    before = (project / ".feather/app.js").read_bytes()
    # Repeated builds must hit the verified content cache.
    cached = subprocess.run([binary, "compile-js", "--project", project], capture_output=True, text=True, check=True)
    assert "unchanged" in cached.stdout, cached.stdout
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    with (Path(temp) / "server.log").open("w+") as log:
        process = subprocess.Popen([binary, "start", "--project", project, "--port", str(port)], stdout=log, stderr=log)
        try:
            for _ in range(200):
                if process.poll() is not None:
                    log.seek(0)
                    raise AssertionError(log.read())
                if "Watching for file changes" in (Path(temp) / "server.log").read_text():
                    break
                time.sleep(0.1)
            else:
                raise AssertionError("Server failed to start")
            ws = WebSocket(port)
            try:
                def text(msg):
                    return json.dumps(msg)
                initial = ws.receive_until(lambda msg: msg.get("type") == "render" and "Kotlin counter: 0" in text(msg))
                descriptor = initial["root"]["children"][2]["action"]
                assert json.loads(descriptor)["type"] == "module_call"
                ws.send_json({"type":"action", "action":descriptor})
                request = ws.receive_until(lambda msg: msg.get("type") == "module_call")
                assert request["module"] == "feather-network" and request["method"] == "get"
                assert request["resultStateKey"] == "response" and request["id"]
                # Simulate a native module result; no external HTTP service needed.
                ws.send_json({"type":"state_update", "key":"response", "value":{"status":200,"data":[1,2]}})
                ws.receive_until(lambda msg: msg.get("type") in ["render", "patch"] and "200" in text(msg))
                ws.send_json({"type": "action", "action": "increment"})
                ws.receive_until(lambda msg: "Kotlin counter: 1" in text(msg))
                updated = original.replace("Kotlin counter:", "Updated Kotlin:").replace("count + 1", "count + 2")
                (project / "App.kt").write_text(updated)
                ws.receive_until(lambda msg: "Updated Kotlin: 1" in text(msg), timeout=180)
                ws.send_json({"type": "action", "action": "increment"})
                ws.receive_until(lambda msg: "Updated Kotlin: 3" in text(msg))
                # A compiler error must keep the last compiled app, code and state.
                good = (project / ".feather/app.js").read_bytes()
                assert good != before
                (project / "App.kt").write_text(updated + "\nfun broken(\n")
                ws.receive_until(lambda msg: msg.get("type") == "render_error", timeout=90)
                assert (project / ".feather/app.js").read_bytes() == good
                (project / "App.kt").write_text(updated)
                # Cached good output is reused, and a full recovery snapshot is sent.
                ws.receive_until(lambda msg: msg.get("type") == "render" and "Updated Kotlin: 3" in text(msg), timeout=90)
                with urllib.request.urlopen(f"http://127.0.0.1:{port}/bundle/manifest", timeout=10) as response:
                    manifest = json.load(response)
                assert manifest["runtime"] == "kotlin-js"
                artifact = manifest["javascript"]
                with urllib.request.urlopen(f"http://127.0.0.1:{port}/bundle/artifact/" + artifact["path"], timeout=10) as response:
                    code = response.read()
                assert hashlib.sha256(code).hexdigest() == artifact["sha256"] and len(code) == artifact["bytes"]
                subprocess.run([binary, "build", "--project", project, "--target", "android"], check=True)
                bundles = list((project / "dist/android/bundles").glob("*/manifest.json"))
                assert len(bundles) == 1
                package = bundles[0].parent
                released = json.loads(bundles[0].read_text())
                assert released["dependencies"] == [] and released["delta"] == {"changed": [], "removed": []}
                assert (package / "app.js").read_bytes() == good
                if len(sys.argv) > 2:
                    checker = Path(sys.argv[2]).resolve()
                    key = project / ".feather/public_key.txt"
                    subprocess.run([checker, package, key], check=True)
                    (package / "app.js").write_bytes(good + b"tamper")
                    rejected = subprocess.run([checker, package, key], capture_output=True, text=True)
                    assert rejected.returncode != 0 and "Artifact verification failed" in rejected.stderr
                assert process.poll() is None
                print("PASS: compiled Kotlin rendering/actions, logic refresh, state retention, compiler error recovery, signed JS artifact, release bundle packaging")
            finally:
                ws.close()
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
