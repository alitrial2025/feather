#!/usr/bin/env python3
"""Exercise CLI startup, bundle delivery, and unsupported compiler failures."""
import hashlib
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
import http.client
sys.dont_write_bytecode = True
from ws_test_client import WebSocket

binary = Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory(prefix="feather-smoke-") as temp:
    project = Path(temp)
    (project / "feather.json").write_text(json.dumps({"entry": "app.fthr"}))
    (project / "app.fthr").write_text('Text("Smoke test")')
    # Unsupported production compilation must fail without generating fake WASM.
    result = subprocess.run([binary, "compile-wasm", "--project", project], capture_output=True, text=True)
    assert result.returncode != 0 and "not implemented" in result.stderr, result
    assert not (project / ".feather/app.wasm").exists()
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    with (project / "server.log").open("w+") as log:
        process = subprocess.Popen([binary, "start", "--project", project, "--port", str(port)], stdout=log, stderr=log)
        try:
            base = f"http://127.0.0.1:{port}"
            for _ in range(100):
                if process.poll() is not None:
                    log.seek(0)
                    raise AssertionError(log.read())
                try:
                    with urllib.request.urlopen(base + "/devtools", timeout=1) as response:
                        assert b"html" in response.read().lower()
                    break
                except OSError:
                    time.sleep(0.1)
            else:
                raise AssertionError("Server did not become ready")
            with urllib.request.urlopen(base + "/bundle/manifest", timeout=10) as response:
                manifest = json.load(response)
            assert "error" not in manifest, manifest
            assert manifest["signature"] and manifest["protocol_version"] == 1
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
            try:
                connection.request("GET", "/ws", headers={
                    "Connection": "Upgrade", "Upgrade": "websocket", "Sec-WebSocket-Version": "13",
                    "Sec-WebSocket-Key": "dGhlIHNhbXBsZSBub25jZQ==", "Origin": "http://10.evil.test:8080",
                })
                response = connection.getresponse()
                assert response.status == 403, response.status
                response.read()
            finally:
                connection.close()
            artifact = manifest["render"]
            with urllib.request.urlopen(base + "/bundle/artifact/" + artifact["path"], timeout=10) as response:
                payload = response.read()
            assert hashlib.sha256(payload).hexdigest() == artifact["sha256"]
            assert len(payload) == artifact["bytes"]
            assert "Smoke test" in payload.decode(), payload
            assert json.loads(payload)["root"]["type"] == "Text"
            # Exercise a real save/refresh cycle, not a server restart or polling GET.
            for _ in range(100):
                if "Watching for file changes" in (project / "server.log").read_text():
                    break
                time.sleep(0.05)
            else:
                raise AssertionError("File watcher did not start")
            ws = WebSocket(port)
            try:
                ws.receive_until(lambda msg: msg.get("type") == "render")
                session = {"count": 7, "profile": {"name": "Ada"}, "items": [True, None, 3]}
                ws.send_json({"type": "devtools_state_update", "key": "session", "value": session})
                ws.receive_until(lambda msg: msg.get("type") == "devtools_state_update" and msg.get("value") == session)
                (project / "app.fthr").write_text('Text("Refreshed without rebuild")')
                patch = ws.receive_until(lambda msg: msg.get("type") == "patch" and "Refreshed without rebuild" in json.dumps(msg))
                assert any(p.get("property") == "text" for p in patch["patches"]), patch
                (project / "app.fthr").write_text('Text(')
                ws.receive_until(lambda msg: msg.get("type") == "render_error")
                (project / "app.fthr").write_text('Text("Recovered")')
                recovered = ws.receive_until(lambda msg: msg.get("type") == "render" and msg.get("root", {}).get("text") == "Recovered")
                assert json.loads(recovered["state"]["session"]) == session
            finally:
                ws.close()
            reconnected = WebSocket(port)
            try:
                snapshot = reconnected.receive_until(lambda msg: msg.get("type") == "render")
                assert snapshot["root"]["text"] == "Recovered"
                assert json.loads(snapshot["state"]["session"]) == session
            finally:
                reconnected.close()
            assert process.poll() is None, "Refresh restarted or crashed the server"
            print("PASS: startup, bundles, origin rejection, live edit, typed state, syntax-error recovery, reconnect")
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
