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
    source = project / "App.kt"
    source.write_text(source.read_text().replace(
        "override fun onCreate() {",
        'override fun onCreate() {\n        println("Kotlin startup log")\n        console("Feather SDK startup log")',
    ).replace(
        'if (action == "increment") {',
        'if (action == "diagnostic-test") error("Kotlin action error")\n        if (action == "increment") {',
    ))
    subprocess.run([binary, "compile-js", "--project", project], check=True)
    original = (project / "App.kt").read_text()
    before = (project / ".feather/app.js").read_bytes()
    # Repeated builds must hit the verified content cache.
    cached = subprocess.run([binary, "compile-js", "--project", project], capture_output=True, text=True, check=True)
    assert "unchanged" in cached.stdout, cached.stdout
    subprocess.run([binary, "build", "--project", project, "--target", "android"], check=True)
    bundle_dir = project / "dist/android/bundles"
    first_bundle, = bundle_dir.glob("*/manifest.json")
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
                ws.send_json({"type": "action", "action": "diagnostic-test"})
                ws.send_json({"type": "log", "platform": "android", "message": "Feather Android log"})
                ws.send_json({"type": "diagnostic", "platform": "ios", "level": "error",
                              "message": "Feather iOS diagnostic\n    at App (app.js:12)"})
                expected_output = [
                    "[Preview LOG] Kotlin startup log",
                    "[Preview LOG] Feather SDK startup log",
                    "[Preview ERROR] JavaScript action failed: Kotlin action error",
                    "[Android LOG] Feather Android log",
                    "[iOS ERROR] Feather iOS diagnostic",
                    "[iOS ERROR]     at App (app.js:12)",
                ]
                deadline = time.monotonic() + 10
                while True:
                    output = (Path(temp) / "server.log").read_text()
                    if all(line in output for line in expected_output):
                        break
                    assert time.monotonic() < deadline, output
                    time.sleep(0.1)
                assert any("app.js:" in line for line in output.splitlines() if line.startswith("[Preview ERROR]")), output
                print("PASS: Kotlin println, SDK console, device logs and named runtime stack trace in the terminal")
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
                sdk_cache_hits = (Path(temp) / "server.log").read_text().count("Reusing compiled Feather SDK")
                updated = original.replace("Kotlin counter:", "Updated Kotlin:").replace("count + 1", "count + 2")
                (project / "App.kt").write_text(updated)
                ws.receive_until(lambda msg: "Updated Kotlin: 1" in text(msg), timeout=180)
                assert (Path(temp) / "server.log").read_text().count("Reusing compiled Feather SDK") > sdk_cache_hits
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
                second_bundle, = (path for path in bundle_dir.glob("*/manifest.json") if path != first_bundle)
                package = second_bundle.parent
                released = json.loads(second_bundle.read_text())
                assert released["dependencies"] == [] and released["delta"] == {"changed": [], "removed": []}
                assert (package / "app.js").read_bytes() == good
                key = project / ".feather/public_key.txt"
                registry = Path(temp) / "releases"
                release = [binary, "release", "--store", registry, "--channel", "android-preview", "--key", key]
                pointer = registry / "channels/android-preview.json"
                for bundle in [first_bundle.parent, package, package]:
                    subprocess.run([*release, "promote", bundle], check=True)
                assert json.loads(pointer.read_bytes()) == {
                    "app": project.name, "current": package.name, "previous": first_bundle.parent.name
                }
                tampered = Path(temp) / "tampered"
                shutil.copytree(package, tampered)
                (tampered / "app.js").write_bytes(good + b"tamper")
                original_pointer = pointer.read_bytes()
                rejected = subprocess.run([*release, "promote", tampered], capture_output=True, text=True)
                assert rejected.returncode != 0 and "Artifact verification failed" in rejected.stderr
                assert pointer.read_bytes() == original_pointer
                subprocess.run([*release, "rollback"], check=True)
                assert json.loads(pointer.read_bytes())["current"] == first_bundle.parent.name
                assert (registry / "bundles" / first_bundle.parent.name / "app.js").read_bytes() == before
                print("PASS: signed release promotion, idempotent activation, tamper rejection, and rollback")
                if len(sys.argv) > 2:
                    checker = Path(sys.argv[2]).resolve()
                    subprocess.run([checker, package, key], check=True)
                    (package / "app.js").write_bytes(good + b"tamper")
                    rejected = subprocess.run([checker, package, key], capture_output=True, text=True)
                    assert rejected.returncode != 0 and "Artifact verification failed" in rejected.stderr
                assert process.poll() is None
                print("PASS: compiled Kotlin rendering/actions, SDK cache reuse, state retention, compiler error recovery, signed JS artifact, release bundle packaging")
            finally:
                ws.close()
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
