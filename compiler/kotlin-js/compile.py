#!/usr/bin/env python3
"""Pinned Kotlin/JS compiler driver. Run from the Feather CLI or directly."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request
import zipfile


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    repo, project = (Path(arg).resolve() for arg in sys.argv[1:3])
    cache = Path(os.environ.get("FEATHER_TOOLS_HOME", str(Path.home() / ".cache/feather")))
    config = json.loads((repo / "compiler/kotlin-js/toolchain.json").read_text())
    cache.mkdir(parents=True, exist_ok=True)
    for artifact in config["artifacts"]:
        target = cache / artifact["path"]
        if not target.exists() or digest(target) != artifact["sha256"]:
            target.parent.mkdir(parents=True, exist_ok=True)
            with urllib.request.urlopen(artifact["url"], timeout=90) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() != artifact["sha256"]:
                raise RuntimeError(f"Checksum mismatch: {artifact['url']}")
            with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as temp:
                temp.write(data)
                temporary = Path(temp.name)
            temporary.replace(target)
    kotlin = cache / "kotlin/kotlinc/lib"
    marker = cache / "kotlin/compiler-version"
    expected = config["artifacts"][0]["sha256"]
    if not (kotlin / "kotlin-compiler.jar").exists() or not marker.exists() or marker.read_text() != expected:
        with zipfile.ZipFile(cache / "compiler.zip") as archive:
            archive.extractall(cache / "kotlin")
        marker.write_text(expected)
    sdk = repo / "sdk/src"
    sources = sorted((sdk / "commonMain").rglob("*.kt")) + sorted((sdk / "jsMain").rglob("*.kt"))
    sources += sorted(path for path in project.rglob("*.kt") if not any(
        part in {".feather", "build", ".git", "target", "node_modules"} for part in path.relative_to(project).parts))
    if not sources:
        raise RuntimeError("No Kotlin sources")
    libraries = [kotlin / "kotlin-stdlib-js.klib", cache / "kotlin-libs/serialization-core.klib", cache / "kotlin-libs/serialization-json.klib"]
    output = project / ".feather"
    output.mkdir(exist_ok=True)
    fingerprint = hashlib.sha256()
    for path in sources + libraries + [repo / "compiler/kotlin-js/toolchain.json", repo / "compiler/kotlin-js/compile.py"]:
        fingerprint.update(str(path).encode())
        fingerprint.update(path.read_bytes())
    signature = fingerprint.hexdigest()
    stamp = output / "javascript-build.json"
    if stamp.exists() and (output / "app.js").exists():
        previous = json.loads(stamp.read_text())
        if previous == {"inputs": signature, "output": digest(output / "app.js")}:
            print("Kotlin/JS unchanged; using compiled artifact")
            return
    java = ["java", "-Xmx2g", "-cp", str(kotlin / "*"), "org.jetbrains.kotlin.cli.js.K2JSCompiler"]
    libs = os.pathsep.join(map(str, libraries))
    # Compiler output is staged: errors never replace the last good application.
    with tempfile.TemporaryDirectory(prefix="js-build-", dir=output) as temporary:
        stage = Path(temporary)
        (stage / "klib").mkdir()
        (stage / "js").mkdir()
        subprocess.run(java + ["-Xir-produce-klib-file", "-ir-output-dir", str(stage / "klib"), "-ir-output-name", "app",
            "-libraries", libs, "-Xplugin=" + str(kotlin / "kotlinx-serialization-compiler-plugin.jar")]
            + list(map(str, sources)), check=True)
        subprocess.run(java + ["-Xir-produce-js", "-Xinclude=" + str(stage / "klib/app.klib"), "-libraries", libs,
            "-ir-output-dir", str(stage / "js"), "-ir-output-name", "app", "-module-kind", "plain", "-main", "call"], check=True)
        (stage / "js/app.js").replace(output / "app.js")
    stamp.write_text(json.dumps({"inputs": signature, "output": digest(output / "app.js")}))
    print("Kotlin/JS compiled: " + str(output / "app.js"))


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError, ValueError) as error:
        print(f"Kotlin/JS build failed: {error}", file=sys.stderr)
        sys.exit(1)
