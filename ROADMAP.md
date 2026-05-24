# Feather Project Roadmap 2.0

Feather is the "Expo Go" for Kotlin Multiplatform (KMP). It enables developers to build truly native, high-performance apps with Kotlin that are delivered dynamically to host shells on Android and iOS.

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
*   [ ] **Bundle Management:** A hosted service to manage and deploy Feather app bundles.
*   [ ] **OTA Updates:** Support for "Push to Device" updates during development and production.
*   [ ] **Deep Linking:** Robust support for routing from web/system directly into specific Feather screens.

---

## 🌈 Phase 3: Ecosystem & Tooling

### 1. IDE Integration
*   [ ] **IntelliJ/Android Studio Plugin:** Syntax highlighting for `.feather` files, live preview, and integrated `feather doctor`.
*   [ ] **VS Code Extension:** Basic support for Feather development on non-JetBrains IDEs.

### 2. Community & Docs
*   [ ] **Plugin SDK:** A formalized way for third-party developers to create and distribute their own Feather native modules.
*   [ ] **Showcase Apps:** High-quality clones of popular apps (e.g., Spotify, Airbnb) built entirely on Feather to demonstrate platform capabilities.

---

## 📉 Known Improvements Needed (Debt)
*   **Error Handling:** Improve the "Red Screen of Death" on mobile devices with better stack traces from the Wasm engine.
*   **Bundle Size:** Further optimize the WASM binary and JSON IR to reduce initial download time.
*   **Diagnostics:** Expand `feather doctor` to check for specific Android NDK and iOS Xcode configuration issues.
*   **QR Scanner Android:** The QR Scanner in `host/android/.../QRScanner.kt` has a known resource issue where the `cameraExecutor` (`Executors.newSingleThreadExecutor()`) is not shut down when the composable leaves the composition. This needs to be wrapped in a `DisposableEffect` to call `shutdown()`.
*   **QR Scanner iOS:** The iOS implementation for `QRScanner` is completely missing or incomplete, preventing feature parity for barcode scanning.
