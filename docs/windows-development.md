# Developing Feather from Windows

## Try the native sample

The cloud build produces these ARM64 Android debug APKs under
`build/native/android/`:

- `feather-counter-debug.apk`: compiled Kotlin application embedded for offline use.
- `feather-dev-host-debug.apk`: persistent host; connect it to a Kotlin/JS dev server.

They contain the Rust interpreter and JNA loader. Build/package checks are not
a substitute for installing and exercising them on a phone. Other Android
architectures are not yet validated. These are debug applications, not store
release binaries.

On Windows with Android platform-tools and an ARM64 Android device connected:

```powershell
adb devices
adb install -r feather-counter-debug.apk
adb shell am start -n dev.feather.host/.MainActivity
```

Run the counter, background/resume the app, and verify that actions work without
a running development server. The sample network button uses a public example
endpoint; a network failure should be surfaced as application data/errors.

## Build using WSL2

The native build driver is validated on Linux. WSL2 lets a Windows workstation
use that workflow. Keep the checkout in the WSL Linux filesystem for performance.
Install Rust, Python 3, JDK 21, a C toolchain/libclang, Android SDK platform 36,
build-tools 34.0.0, and NDK 27.0.12077973. The Android wrapper pins Gradle 8.9 and
its distribution checksum.

```sh
export FEATHER_TOOLS_HOME="$HOME/.cache/feather"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
rustup target add aarch64-linux-android
python3 scripts/build_android_sample.py
python3 scripts/build_android_sample.py --dev-host
```

The script builds ARM64 Rust, compiles Kotlin/JS, verifies the signed runtime
bundle, stages assets outside tracked source, builds the APK, and checks its
contents and native ELF/ZIP alignment for 16 KB Android memory pages. It builds
only the selected ARM64 slice. Set `CARGO_TARGET_DIR` to reuse an existing Rust
cache. `FEATHER_TOOLS_HOME` retains checksum-pinned compiler artifacts.

The development host is paired to the project's signing key. Build the host
from the same project/cache used to serve the app. Its private signing key stays
in ignored `.feather/` storage and must not be committed. For multiple projects,
interactive development pairing/key rotation is still a future capability.

## Edit and refresh

After installing the development host, start the CLI in WSL2:

```sh
cargo build --locked --manifest-path cli/Cargo.toml
cli/target/debug/feather-cli start --project samples/runtime-counter
```

Use `$CARGO_TARGET_DIR/debug/feather-cli` when a custom target directory is set.
With Windows-to-WSL localhost forwarding working, use USB forwarding:

```powershell
adb reverse tcp:8080 tcp:8080
```

Open the host launcher and connect to `127.0.0.1`. Edit `App.kt`. The server
compiles changed Kotlin and sends a code-change notification. The host fetches
and verifies the signed source artifact, then replaces its interpreter while
retaining compatible state. Native dependency/permission changes require a host
rebuild; ordinary application code edits do not. Changed Kotlin currently uses
a full compiler invocation, so refresh latency is not yet optimized.

The hosted CLI can also execute a server-side preview. Once native JavaScript
execution is active, server preview trees do not replace device state. The
current DevTools preview is shared/last-writer; per-device debugging sessions,
revision acknowledgements, replay/resync and state migrations remain work.

## iOS without owning a Mac

The repository includes `host/ios/project.yml`, a macOS build driver, and an iOS
GitHub Actions job. A hosted macOS runner can build the simulator app using
Xcode and XcodeGen without a signing account. This is the route for a Windows
maintainer to obtain iOS compile evidence.

```sh
# On a macOS runner with Xcode, JDK 21 and XcodeGen:
python3 scripts/build_ios_sample.py
```

The CI job still needs to execute successfully; the Linux cloud environment
cannot establish that it passes. iPhone installation, device tests, release
signing, entitlements and store packaging remain separate acceptance gates.
