#!/usr/bin/env python3
"""Build a signed-runtime ARM64 sample inside a debug Android host APK."""
import argparse
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import zipfile

repo = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--project", type=Path, default=repo / "samples/runtime-counter")
parser.add_argument("--dev-host", action="store_true", help="Build a persistent host without embedding application code")
options = parser.parse_args()
project = options.project.resolve()
environment = os.environ.copy()
target = Path(environment.get("CARGO_TARGET_DIR", str(repo / "core/target"))).resolve()
environment["CARGO_TARGET_DIR"] = str(target)
ndk = environment.get("ANDROID_NDK_HOME") or environment.get("ANDROID_NDK_ROOT")
if not ndk:
    ndk = str(Path(environment.get("ANDROID_HOME") or environment.get("ANDROID_SDK_ROOT", "")) / "ndk/27.0.12077973")
host = {"linux": "linux-x86_64", "darwin": "darwin-x86_64"}.get(sys.platform)
if host is None:
    raise SystemExit("This build driver currently supports Linux/macOS hosts")
llvm = Path(ndk) / "toolchains/llvm/prebuilt" / host / "bin"
clang = llvm / "aarch64-linux-android24-clang"
if not clang.is_file():
    raise SystemExit("Install NDK 27.0.12077973 and set ANDROID_NDK_HOME (or ANDROID_HOME)")
cross = environment.copy()
cross["CC_aarch64_linux_android"] = str(clang)
cross["AR_aarch64_linux_android"] = str(llvm / "llvm-ar")
cross["CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"] = str(clang)
resource_dir = subprocess.check_output([str(clang), "--print-resource-dir"], text=True).strip()
cross["BINDGEN_EXTRA_CLANG_ARGS_aarch64_linux_android"] = "--sysroot=" + str(llvm.parent / "sysroot") + " --target=aarch64-linux-android24 -resource-dir=" + resource_dir
cross["CARGO_BUILD_JOBS"] = "2"
cross["RUSTFLAGS"] = (cross.get("RUSTFLAGS", "") + " -C link-arg=-Wl,-z,max-page-size=16384").strip()

def run(arguments, env=environment):
    subprocess.run(list(map(str, arguments)), cwd=repo, env=env, check=True)

run(["cargo", "build", "--locked", "--release", "--lib", "--manifest-path", repo / "core/Cargo.toml", "--target", "aarch64-linux-android"], cross)
run(["cargo", "build", "--locked", "--manifest-path", repo / "cli/Cargo.toml"])
run(["cargo", "build", "--locked", "--manifest-path", repo / "core/Cargo.toml", "--bin", "feather-bundle-check"])
cli = target / "debug/feather-cli"
result = subprocess.run([cli, "build", "--project", project, "--target", "android"], cwd=repo, env=environment, capture_output=True, text=True)
print(result.stdout)
print(result.stderr, file=sys.stderr)
result.check_returncode()
line = next(line for line in result.stdout.splitlines() if line.startswith("Experimental signed Kotlin/JS bundle: "))
bundle = Path(line.split(": ", 1)[1]).resolve()
key = project / ".feather/public_key.txt"
run([target / "debug/feather-bundle-check", bundle, key])
jni = repo / "build/native/android/jniLibs"
(jni / "arm64-v8a").mkdir(parents=True, exist_ok=True)
library = target / "aarch64-linux-android/release/libfeather_core.so"
shutil.copy2(library, jni / "arm64-v8a/libfeather_core.so")
run([repo / "host/android/gradlew", "-p", repo / "host/android", ":app:assembleDebug", "--no-daemon", "--max-workers=2",
    "-PfeatherAbis=arm64-v8a", "-PfeatherJniLibs=" + str(jni),
    "-PfeatherBundlePublicKey=" + key.read_text().strip()] + ([] if options.dev_host else ["-PfeatherBundleDir=" + str(bundle)]))
apk = repo / "host/android/app/build/outputs/apk/debug/app-debug.apk"
with zipfile.ZipFile(apk) as archive:
    if options.dev_host:
        assert "assets/feather/app.js" not in archive.namelist(), "Stale application assets in development host"
    else:
        assert archive.read("assets/feather/app.js") == (bundle / "app.js").read_bytes()
        assert json.loads(archive.read("assets/feather/manifest.json")) == json.loads((bundle / "manifest.json").read_text())
    native = archive.read("lib/arm64-v8a/libfeather_core.so")
    assert native[:4] == b"\x7fELF" and struct.unpack_from("<H", native, 18)[0] == 183
    for name in ["lib/arm64-v8a/libfeather_core.so", "lib/arm64-v8a/libjnidispatch.so"]:
        data = archive.read(name)
        table = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        alignments = [struct.unpack_from("<Q", data, table + index * size + 48)[0]
            for index in range(count) if struct.unpack_from("<I", data, table + index * size)[0] == 1]
        assert alignments and all(alignment >= 16384 for alignment in alignments), (name, alignments)
        info = archive.getinfo(name)
        if info.compress_type == zipfile.ZIP_STORED:
            with apk.open("rb") as raw:
                raw.seek(info.header_offset)
                header = raw.read(30)
            name_length, extra_length = struct.unpack_from("<HH", header, 26)
            assert (info.header_offset + 30 + name_length + extra_length) % 16384 == 0, name
artifact = repo / "build/native/android" / ("feather-dev-host-debug.apk" if options.dev_host else "feather-counter-debug.apk")
shutil.copy2(apk, artifact)
print("PASS: verified application asset mode, ARM64 runtime and 16 KB alignment in " + str(artifact))
print("Debug APK only; install/run on an ARM64 device before claiming device readiness.")
