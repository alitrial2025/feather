#!/usr/bin/env python3
"""Build a simulator application on macOS; no signing account is required."""
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys

if sys.platform != "darwin":
    raise SystemExit("iOS simulator builds require macOS/Xcode; use the macOS CI job from Windows.")
repo = Path(__file__).resolve().parents[1]
project = repo / "samples/runtime-counter"
environment = os.environ.copy()
target = Path(environment.get("CARGO_TARGET_DIR", str(repo / "core/target"))).resolve()
environment["CARGO_TARGET_DIR"] = str(target)
arch = "arm64" if platform.machine() == "arm64" else "x86_64"
triple = "aarch64-apple-ios-sim" if arch == "arm64" else "x86_64-apple-ios"
sdk = subprocess.check_output(["xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"], text=True).strip()
clang = subprocess.check_output(["xcrun", "--sdk", "iphonesimulator", "--find", "clang"], text=True).strip()
cross = environment.copy()
cross["SDKROOT"] = sdk
cross["IPHONEOS_DEPLOYMENT_TARGET"] = "16.0"
cross["LIBCLANG_PATH"] = str(Path(clang).parent.parent / "lib")
cross["CC_" + triple.replace("-", "_")] = clang
cross["BINDGEN_EXTRA_CLANG_ARGS_" + triple.replace("-", "_")] = "-isysroot " + sdk + " --target=" + arch + "-apple-ios16.0-simulator"
cross["CARGO_BUILD_JOBS"] = "2"

def run(args, env=environment, cwd=repo):
    subprocess.run(list(map(str, args)), env=env, cwd=cwd, check=True)

run(["rustup", "target", "add", triple])
run(["cargo", "build", "--locked", "--release", "--lib", "--manifest-path", repo / "core/Cargo.toml", "--target", triple], cross)
native = repo / "build/native/ios-sim"
native.mkdir(parents=True, exist_ok=True)
shutil.copy2(target / triple / "release/libfeather_core.a", native / "libfeather_core.a")
run(["cargo", "build", "--locked", "--manifest-path", repo / "cli/Cargo.toml"])
result = subprocess.run([target / "debug/feather-cli", "build", "--project", project, "--target", "ios"], env=environment, cwd=repo, capture_output=True, text=True)
print(result.stdout)
print(result.stderr, file=sys.stderr)
result.check_returncode()
line = next(line for line in result.stdout.splitlines() if line.startswith("Experimental signed Kotlin/JS bundle: "))
bundle = Path(line.split(": ", 1)[1]).resolve()
assets = repo / "host/ios/build/runtime-assets/feather"
if assets.exists(): shutil.rmtree(assets)
shutil.copytree(bundle, assets)
run(["xcodegen", "generate", "--spec", "project.yml"], cwd=repo / "host/ios")
run(["xcodebuild", "-project", repo / "host/ios/FeatherHost.xcodeproj", "-scheme", "FeatherHost", "-configuration", "Debug",
    "-sdk", "iphonesimulator", "-destination", "generic/platform=iOS Simulator", "ARCHS=" + arch,
    "ONLY_ACTIVE_ARCH=YES", "CODE_SIGNING_ALLOWED=NO", "FEATHER_BUNDLE_PUBLIC_KEY=" + (project / ".feather/public_key.txt").read_text().strip(),
    "-derivedDataPath", repo / "host/ios/build/DerivedData", "build"])
print("Built simulator app; launch/runtime/device validation remains required.")
