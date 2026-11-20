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


def fingerprint(paths):
    checksum = hashlib.sha256()
    for path in paths:
        checksum.update(str(path).encode() + b"\0")
        checksum.update(bytes.fromhex(digest(path)))
    return checksum.hexdigest()


def compile_klib(java, sources, libraries, plugin, output, name):
    subprocess.run(java + [
        "-Xir-produce-klib-file", "-ir-output-dir", str(output), "-ir-output-name", name,
        "-libraries", os.pathsep.join(map(str, libraries)), "-Xplugin=" + str(plugin),
    ] + list(map(str, sources)), check=True)


def sdk_library(java, sources, libraries, toolchain, plugin, cache):
    signature = fingerprint(sources + libraries + toolchain)
    entry = cache / "sdk" / signature
    entry.mkdir(parents=True, exist_ok=True)
    library = entry / "feather-sdk.klib"
    stamp = entry / "sha256"
    if library.exists() and stamp.exists() and stamp.read_text() == digest(library):
        print("Reusing compiled Feather SDK")
        return library
    with tempfile.TemporaryDirectory(prefix="sdk-build-", dir=entry) as temporary:
        stage = Path(temporary)
        compile_klib(java, sources, libraries, plugin, stage, "feather-sdk")
        compiled = stage / library.name
        (stage / "sha256").write_text(digest(compiled))
        compiled.replace(library)
        (stage / "sha256").replace(stamp)
    print("Compiled Feather SDK")
    return library


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
    sdk_sources = sorted((sdk / "commonMain").rglob("*.kt")) + sorted((sdk / "jsMain").rglob("*.kt"))
    app_sources = sorted(path for path in project.rglob("*.kt") if not any(
        part in {".feather", "build", ".git", "target", "node_modules"} for part in path.relative_to(project).parts))
    if not app_sources:
        raise RuntimeError("No Kotlin app sources")
    libraries = [kotlin / "kotlin-stdlib-js.klib", cache / "kotlin-libs/serialization-core.klib", cache / "kotlin-libs/serialization-json.klib"]
    output = project / ".feather"
    output.mkdir(exist_ok=True)
    toolchain = [repo / "compiler/kotlin-js/toolchain.json", Path(__file__).resolve()]
    signature = fingerprint(sdk_sources + app_sources + libraries + toolchain)
    stamp = output / "javascript-build.json"
    if stamp.exists() and (output / "app.js").exists():
        previous = json.loads(stamp.read_text())
        if previous == {"inputs": signature, "output": digest(output / "app.js")}:
            print("Kotlin/JS unchanged; using compiled artifact")
            return
    java = ["java", "-Xmx2g", "-cp", str(kotlin / "*"), "org.jetbrains.kotlin.cli.js.K2JSCompiler"]
    plugin = kotlin / "kotlinx-serialization-compiler-plugin.jar"
    libraries.append(sdk_library(java, sdk_sources, libraries, toolchain, plugin, cache))
    libs = os.pathsep.join(map(str, libraries))
    # Compiler output is staged: errors never replace the last good application.
    with tempfile.TemporaryDirectory(prefix="js-build-", dir=output) as temporary:
        stage = Path(temporary)
        (stage / "klib").mkdir()
        (stage / "js").mkdir()
        compile_klib(java, app_sources, libraries, plugin, stage / "klib", "app")
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
