# Feather Current Gap Map

This file maps the current codebase against the platform contract in `docs/platform-contract.md`.

## What Already Exists

### SDK

- Broad widget surface exists in `sdk/src/commonMain/kotlin/dev/feather/sdk/Feather.kt`.
- Standard module-like APIs exist for network, storage, camera, location, notifications, files, sensors, share, auth, and navigation.
- Media widgets exist for `VideoPlayer` and `AudioPlayer`.
- `WebView` widget exists.

### Core

- `AppRuntime` owns typed state and action application.
- `NativeModule` and `NativeCallback` traits exist.
- Module registration exists through `register_module`.
- WASM host function `call_module` exists.
- Bundle manifest and module discovery exist.

### CLI

- `feather start` serves render payloads.
- Bundle manifest generation exists.
- Asset copying exists.
- Module discovery from render JSON exists.
- `feather install` writes `feather.json` and `feather.lock`.

### Android Host

- Native Compose renderer exists.
- Dev-server WebSocket client exists.
- Bundle manifest/artifact fetch exists.
- Network `GET`, `POST`, `PUT`, `DELETE` host commands exist.
- SharedPreferences storage commands exist.
- Media3 video rendering exists.
- Audio player rendering has started.
- WebView rendering has started.

## Major Gaps

### Contract Drift

- SDK module APIs currently return raw string commands.
- Android host parses `host:` string commands directly.
- Core has a real module bridge, but Android host does not use it yet.
- CLI/core/host do not enforce a single structured module-call schema yet.

### Module Registry

- Android now has a first-pass `ModuleRegistry` and `FeatherModule` interface.
- `feather-network` and `feather-storage` have first-pass Android implementations.
- `feather-media` and `feather-webview` have first-pass Android render modules.
- Third-party module registration is documented but not implemented end to end.
- Missing-module diagnostics are not enforced before render.

### Compiler

- KSP still scans source text.
- CLI fallback parser still scans source text.
- Real Kotlin constructs are only partially supported.
- Stable node IDs are missing.
- Diagnostics lack robust file/line/column accuracy.

### CLI

- `feather install` does not resolve real module metadata.
- `feather doctor` does not validate host/module compatibility.
- Bundle manifest does not include permission requirements.
- Production build path is still skeletal.

### Host Structure

- Android host is too concentrated in `MainActivity.kt`.
- Renderer, parsing, style mapping, modules, and dev-client code need separation.
- iOS parity still needs to be checked or built.

## Priority Order

1. Teach Android host to report missing module diagnostics.
2. Add SDK structured request models while keeping legacy string commands.
3. Update CLI `doctor` to check declared modules against known official modules.
4. Expand bundle manifest with permissions.
5. Start compiler/KSP cleanup once module boundaries are stable.

## Rule For New Features

Do not add a new native feature directly inside app code or as a one-off host branch.

Every new capability should answer:

1. Which SDK API exposes it?
2. Which protocol message carries it?
3. Which core runtime path owns state/result handling?
4. Which module implements it on Android?
5. Which module implements it on iOS or how is unsupported-platform reported?
6. Which CLI/doctor validation protects the developer?
