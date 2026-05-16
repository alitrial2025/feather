# Feather Project Roadmap 2.0

Feather is the "Expo Go" for Kotlin Multiplatform (KMP). It enables developers to build truly native, high-performance apps with Kotlin that are delivered dynamically to host shells on Android and iOS.

## 🎯 Vision
To provide a seamless developer experience where Kotlin logic and native UI are unified, dynamic, and cross-platform without the overhead of traditional cross-platform frameworks.

---

## 🛠 Phase 1: Core Stability & Production Readiness (Current Focus)

### 1. Robust WasmGC Engine
*   [x] **Instruction Parity:** Added support for `ref.test`, `ref.cast`, and default allocators.
*   [x] **Automatic Type Discovery:** Runtime now parses WASM TypeSection to register GC structures.
*   [ ] **Performance:** Optimize the bridge between `wasmi` and the Rust `GcHeap` to minimize transition overhead.

### 2. The Feather UI Language (FUI) - *NEW STRATEGIC FOCUS*
*   [ ] **Dedicated Grammar:** Implement a custom DSL (.fthr) using `pest` or `nom` for instant, 100% reliable UI parsing.
*   [ ] **Instant Hot-Reload:** bypass the Wasm compilation step for UI-only changes, enabling sub-100ms updates.
*   [ ] **Unified IR Transpiler:** A single tool to emit optimized binary IR from both Kotlin and FUI sources.

### 3. Binary IR & Protocol 1.0
*   [ ] **JSON to FlexBuffers:** Replace `app.feather.json` with **FlexBuffers** for zero-copy binary deserialization on mobile hosts (schema-less FlatBuffers).
*   [x] **MessagePack Bridge:** (Done) Implemented high-speed communication for internal state and module calls.
*   [ ] **Streamed Rendering:** Support for "streaming" the UI tree for extremely large lists or complex views.

### 4. Native Host Parity
*   [ ] **Android:** Polish the Jetpack Compose renderer for complex layouts and animations.
*   [ ] **iOS:** Achieve 1:1 feature parity in the SwiftUI renderer, including advanced components like `Pager` and `BottomSheet`.
*   [ ] **Shared Modules:** Standardize the `NativeModule` interface to ensure that a module added to Android is easily mirrored on iOS.

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
