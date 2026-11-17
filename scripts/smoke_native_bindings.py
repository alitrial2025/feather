#!/usr/bin/env python3
"""Exercise generated Kotlin bindings against the actual host Rust library."""
import argparse
import hashlib
import json
import shutil
import socket
import tempfile
import time
import os
from pathlib import Path
import subprocess
import sys
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--refresh", action="store_true")
options = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
tools = Path(os.environ.get("FEATHER_TOOLS_HOME", str(Path.home() / ".cache/feather")))
target = Path(os.environ.get("CARGO_TARGET_DIR", str(repo / "core/target"))).resolve()
environment = os.environ.copy()
environment["CARGO_TARGET_DIR"] = str(target)
subprocess.run(["cargo", "build", "--locked", "--manifest-path", repo / "core/Cargo.toml", "--lib"], cwd=repo, env=environment, check=True)
subprocess.run(["python3", repo / "compiler/kotlin-js/compile.py", repo, repo / "samples/runtime-counter"], env=environment, check=True)
jna = tools / "jna/jna.jar"
expected = "4fb141dd8ef6b0585ffceea4bc49602fbc6312fa977e2c488794ea3e6aafecae"
if not jna.exists() or hashlib.sha256(jna.read_bytes()).hexdigest() != expected:
    with urllib.request.urlopen("https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.19.1/jna-5.19.1.jar", timeout=90) as response:
        data = response.read()
    assert hashlib.sha256(data).hexdigest() == expected, "JNA checksum mismatch"
    jna.parent.mkdir(parents=True, exist_ok=True)
    jna.write_bytes(data)
kotlin = tools / "kotlin/kotlinc"
output = tools / "native-binding-smoke"
output.mkdir(exist_ok=True)
(output / "tmp").mkdir(exist_ok=True)
jar = output / "smoke.jar"
subprocess.run(["java", "-Xmx1g", "-cp", str(kotlin / "lib/*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
    "-kotlin-home", kotlin, "-classpath", jna, "-jvm-target", "17", "-include-runtime", "-d", jar,
    repo / "host/android/app/src/main/java/uniffi/feather_core/feather_core.kt", repo / ("scripts/fixtures/NativeRefreshSmoke.kt" if options.refresh else "scripts/fixtures/RuntimeBindingsSmoke.kt")], check=True)
command = ["java", "-Djna.library.path=" + str(target / "debug"), "-Djna.tmpdir=" + str(output / "tmp"),
    "-cp", os.pathsep.join([str(jar), str(jna)])]
if not options.refresh:
    subprocess.run(command + ["RuntimeBindingsSmokeKt", repo / "samples/runtime-counter/.feather/app.js"], check=True)
else:
    subprocess.run(["cargo", "build", "--locked", "--manifest-path", repo / "cli/Cargo.toml"], env=environment, check=True)
    with tempfile.TemporaryDirectory(prefix="feather-native-refresh-") as temporary:
        project = Path(temporary) / "counter"
        shutil.copytree(repo / "samples/runtime-counter", project, ignore=shutil.ignore_patterns(".feather", ".featherbundle", "dist"))
        with socket.socket() as port_socket:
            port_socket.bind(("127.0.0.1", 0))
            port = port_socket.getsockname()[1]
        with (Path(temporary) / "server.log").open("w+") as log:
            server = subprocess.Popen([target / "debug/feather-cli", "start", "--project", project, "--port", str(port)], env=environment, stdout=log, stderr=log)
            try:
                for _ in range(1800):
                    if server.poll() is not None:
                        log.seek(0)
                        raise AssertionError(log.read())
                    if "Watching for file changes" in (Path(temporary) / "server.log").read_text(): break
                    time.sleep(0.1)
                else: raise AssertionError("Compiler/server startup timed out")
                with urllib.request.urlopen(f"http://127.0.0.1:{port}/bundle/manifest", timeout=10) as response:
                    assert "error" not in json.load(response)
                subprocess.run(command + ["NativeRefreshSmokeKt", f"http://127.0.0.1:{port}", project / ".feather/public_key.txt", project / "App.kt"], check=True)
            finally:
                server.terminate()
                try: server.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    server.kill()
                    server.wait(timeout=5)
