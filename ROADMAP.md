# Feather Project Roadmap 2.0

Feather is the "Expo Go" for Kotlin Multiplatform (KMP). It enables developers to build truly native, high-performance apps with Kotlin that are delivered dynamically to host shells on Android and iOS.

## Expo and React Native parity milestones

The acceptance checks below define completion of each milestone on supported
Android and iOS versions. Feather Chat exercises the development workflow.

| # | Milestone | Acceptance check |
|---|---|---|
| 1 | Versioned SDK-to-host contract | Widget, property, and action fixtures pass both native parsers and render on both hosts. `feather-cli inspect` now checks Kotlin/JS widget types and reports paths for incompatible widgets. |
| 2 | Responsive and accessible layout | Chat works across screen sizes with keyboard avoidance, safe areas, dark mode, font scaling, and screen readers. |
| 3 | One-command project setup | A fresh checkout can generate a counter or chat app and start a private QR session. Both starters and the server smoke flow are available. Validate pairing on devices. |
| 4 | Fast Refresh | An edit preserves compatible state and leaves the last working bundle available after a compile failure. The Kotlin compiler now reuses the compiled SDK on app edits. Measure save-to-screen time and reconnection on devices. |
| 5 | Feather Go on iPhone | Pair by QR on a physical iPhone, load a signed bundle, refresh, and reconnect. Record the tested Android and iOS versions. |
| 6 | Developer tools | Stream logs in the terminal, map errors to Kotlin source lines, and inspect layout bounds and unsupported properties. Preview log streaming is covered by the Kotlin smoke test. |
| 7 | Typed navigation and deep links | Nested stacks, tabs, modals, typed parameters, and native back behavior handle cold and warm links. |
| 8 | Assets | Fonts, icons, images, and localized strings are bundled, cached, and selected by density and locale. |
| 9 | Lists and images | Stable keys, pagination, and image prefetch preserve scroll position during chat and feed updates. |
| 10 | Gestures and animations | Swipe, drag, springs, and transitions remain responsive while app code runs. |
| 11 | Custom development builds and plugins | A sample adds a native dependency, exposes it through Kotlin, and pairs with a custom build. |
| 12 | Persistent and secure storage | Preferences, SQLite migrations, and secure credentials survive process restarts. |
| 13 | Push notifications | Device registration, foreground and background delivery, and tap-to-route work on both platforms. |
| 14 | Device APIs | Camera, photo picker, file access, and sharing handle success, denial, cancellation, and failure. |
| 15 | Cloud projects and previews | Multiple projects get stable private links, access control, and pull-request previews. |
| 16 | Release builds | CI can reproduce signed Android App Bundles and iOS archives for development, staging, and production. |
| 17 | OTA updates | Release channels enforce host compatibility and support staged rollout and rollback. |
| 18 | Test and performance tooling | CI runs cross-platform device tests, screenshot comparisons, and startup, scrolling, and refresh benchmarks. |
| 19 | Production diagnostics | Opt-in reports identify app, host build, and signed bundle. Local Kotlin/JS records are implemented. Device validation, native crash reporting, and source mapping remain. |
| 20 | Package ecosystem | Versioned SDK releases ship with a compatibility guide, documented plugins, and tested starter apps. |

## 🎯 Vision
To provide a seamless developer experience where Kotlin logic and native UI are unified, dynamic, and cross-platform without the overhead of traditional cross-platform frameworks.

---

## 🛠 Phase 1: Core Stability & Production Readiness (Completed & Hardened!)

### 1. Robust WasmGC Engine
*   [x] **Instruction Parity:** Added support for `ref.test`, `ref.cast`, and default allocators.
*   [x] **Automatic Type Discovery:** Runtime now parses WASM TypeSection to register GC structures.
*   [x] **Performance & JIT-Free Parity:** Optimized bridge between `wasmi` and the Rust `GcHeap` to support JIT-ban bypassing on physical iOS platforms.

### 2. The Feather UI Language (FUI) - *COMPLETED*
*   [x] **Dedicated Grammar:** Implemented a custom DSL (.fthr) using `pest` supporting dot-notation and advanced math expressions.
*   [x] **Instant Hot-Reload:** Localized path-mutation delta streams update nested layout structures directly.
*   [x] **Unified IR Transpiler:** Complete Pest-driven Abstract Syntax Tree parser mapping compiler definitions directly.

### 3. Binary IR & Protocol 1.0 - *COMPLETED*
*   [x] **Schema-driven FlatBuffers:** Replaced schema-less serialization with binary zero-copy deserialization over a formal `layout.fbs` schema.
*   [x] **MessagePack Bridge:** High-speed binary host-core bridge for actions and state synchronization.
*   [x] **Incremental Delta Engine:** Streams sub-10ms UI diff patches over WebSockets instead of complete reloads.

### 4. Native Host Parity - *COMPLETED*
*   [x] **Android Compose Renderer:** Complete zero-copy FlatBuffers binary layout walker.
*   [x] **iOS SwiftUI Renderer:** Interactive real-time delta patch mutations utilizing copy-on-write Swift value traversal.
*   [x] **Shared Modules:** Standardized `FeatherPlugin` registry allowing seamless native feature reflection on both mobile hosts.

---

## 🚀 Phase 2: The "Expo Go" Experience

### 1. Official Module Library
*   [ ] **feather-camera:** Full implementation of barcode scanning (MLKit) and photo/video capture.
*   [ ] **feather-maps:** Native Google Maps (Android) and MapKit (iOS) integration with shared Kotlin API.
*   [ ] **feather-auth:** Biometric (FaceID/Fingerprint) and Social Login (Google/Apple) support.
*   [ ] **feather-payments:** Native Apple Pay and Google Pay integration.

### 2. Feather Cloud (Dynamic Updates)
*   [x] **Local release staging:** Verify signed Kotlin/JS bundles, promote them by channel, and roll back to a verified previous build. [Release channels](docs/release-channels.md)
*   [ ] **Bundle Management:** A hosted service to manage and deploy Feather app bundles.
*   [ ] **OTA Updates:** Support for "Push to Device" updates during development and production.
*   [ ] **Deep Linking:** Standalone Kotlin/JS hosts can pass signed `feather://app` paths into apps. Verify cold and warm launch on devices and add HTTPS App Links and Universal Links.

---

## 🌈 Phase 3: Ecosystem & Tooling

### 1. IDE Integration
*   [ ] **IntelliJ/Android Studio Plugin:** Syntax highlighting for `.feather` files, live preview, and integrated `feather doctor`.
*   [x] **VS Code Extension:** Local VSIX with `.fthr` highlighting, snippets, bracket pairing, and an integrated Doctor task. [Local extension](tools/vscode/README.md)

### 2. Community & Docs
*   [ ] **Plugin SDK:** A formalized way for third-party developers to create and distribute their own Feather native modules.
*   [x] **Device Info sample:** Kotlin starter demonstrating a shared native module call and a typed result. [Device Info](samples/device-info/README.md)
*   [ ] **Showcase Apps:** High-quality clones of popular apps (e.g., Spotify, Airbnb) built entirely on Feather to demonstrate platform capabilities.

---

## 📉 Known Improvements Needed (Debt)
*   **Error Handling:** Improve the "Red Screen of Death" on mobile devices with better stack traces from the Wasm engine.
*   **Bundle Size:** Further optimize the WASM binary and JSON IR to reduce initial download time.
*   **Diagnostics:** Expand `feather doctor` to check for specific Android NDK and iOS Xcode configuration issues.
*   **QR Scanner Android:** The QR Scanner in `host/android/.../QRScanner.kt` has a known resource issue where the `cameraExecutor` (`Executors.newSingleThreadExecutor()`) is not shut down when the composable leaves the composition. This needs to be wrapped in a `DisposableEffect` to call `shutdown()`.
*   **QR Scanner iOS:** The iOS implementation for `QRScanner` is completely missing or incomplete, preventing feature parity for barcode scanning.
