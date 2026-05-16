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
- Bundle manifest and module discovery (with platforms and permissions) exist.
- Diagnostic buffering and missing-module/unsupported-platform capability checks are implemented.

### CLI

- `feather start` serves render payloads.
- Bundle manifest generation exists.
- Asset copying exists.
- Module discovery from render JSON exists.
- `feather install` writes `feather.json` and `feather.lock`.

### Host Structure (Android & iOS)

- Native rendering engines (Compose and SwiftUI) exist.
- Dev-server WebSocket client exists on both platforms.
- Bundle manifest/artifact fetch exists.
- Both platforms instantiate the Rust `AppRuntime` and route state/actions through it.
- Both platforms have a `ModuleRegistry` and `FeatherModule` interface/protocol.
- Official modules (Network, Storage, Navigation, Media, WebView, Device) are registered and act as targets for structured module calls or legacy string commands.
- Lifecycle hooks (initialize, start, stop, destroy, etc.) are implemented and wired up.
- Diagnostic overlays exist and display missing module/platform errors discovered via the bundle manifest.

## Major Gaps

### Contract Drift

- SDK module APIs currently return raw string commands.
- CLI/core/host do not enforce a single structured module-call schema end-to-end for all features yet.

### Module Capabilities

- The official modules (`feather-network`, `feather-storage`, `feather-media`, `feather-webview`, etc.) are registered but need their implementations fleshed out with full capabilities (e.g., HTTP POST, JSON bodies, advanced playback controls).
- Third-party module registration is documented but not fully automated via the CLI end-to-end.

### Compiler

- KSP still scans source text.
- CLI fallback parser still scans source text.
- Real Kotlin constructs are only partially supported.
- Stable node IDs are missing.
- Diagnostics lack robust file/line/column accuracy.

### CLI

- `feather install` does not resolve real module metadata.
- `feather doctor` does not fully validate host/module compatibility against the manifest.
- Production build path is still skeletal.

## Priority Order

1. Flesh out implementations for Core Official Modules (`feather-network`, `feather-storage`, `feather-media`, `feather-webview`, `feather-camera`).
2. Add SDK structured request models while phasing out legacy string commands.
3. Update CLI `doctor` to check declared modules against known official modules and permissions.
4. Start compiler/KSP cleanup once module boundaries are stable.

## Rule For New Features

Do not add a new native feature directly inside app code or as a one-off host branch.

Every new capability should answer:

1. Which SDK API exposes it?
2. Which protocol message carries it?
3. Which core runtime path owns state/result handling?
4. Which module implements it on Android?
5. Which module implements it on iOS or how is unsupported-platform reported?
6. Which CLI/doctor validation protects the developer?
