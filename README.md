# Feather 🪶

> Experimental: the Rust workflow and an opt-in Kotlin/JS compiler/runtime
> slice are tested. Mobile production delivery is not release-ready. See the
> [production readiness review](docs/production-readiness.md) for verified behavior
> and remaining release gates.

**Feather** is a next-generation high-performance native delivery platform for Kotlin Multiplatform (KMP). It enables developers to build truly native apps with Kotlin that are delivered dynamically to host shells on Android and iOS with near-instant hot-reloads, stateful fast refresh, and zero-copy binary performance.

---

## 🚀 Key Features

- **Stateful Fast Refresh**: Hot-reload Kotlin WASM and UI layouts instantly over the air *without losing active application state or navigation history*.
- **Truly Native**: Implements pixel-perfect, native rendering using Jetpack Compose (Android) and SwiftUI (iOS).
- **Interactive DevTools Dashboard**: Built-in glassmorphic debugger served straight from the CLI (`/devtools`), featuring a live state editor, collapsible layout tree explorer, time-travel ledger, and streaming device log console.
- **Robust AST-Based Compiler**: Upgraded KSP compiler using semantic Kotlin AST visitors and character-based brace scanning for highly deterministic widget trees.
- **Developer Error Boundary ("Red Screen of Death")**: High-aesthetic deep crimson developer overlays on Android and iOS displaying styled scrollable monospace logs and instant "FORCE RELOAD BUNDLE" action triggers.
- **Universal Host Plugin SDK**: Dynamic runtime packaging specification (via SPI ServiceLoader on Android and namespace-aware Class Reflection on iOS) to discover and load native plugins dynamically.
- **Expanded FUI Pest Grammar**: Extended the custom Feather UI Language (.fthr) to support dot-notation access (e.g. `state.user.name`) and advanced math functions (`min()`, `max()`).
- **Production Hardened Security**:
  - **CSWSH Protection**: Active Origin verification protecting `/ws` loopback connections against Cross-Site WebSocket Hijacking.
  - **Path-Traversal Sanitizer**: Component-based splits-filtering of all bundle requests (`/bundle/artifact/{path}`) to enforce sandbox confinement.

---

## 🏗️ Architecture: The High-Performance Pivot

Feather moves beyond simple JSON rendering to a professional-grade binary pipeline:

```
Source (.fthr / .kt) ➔ FUI Compiler (Rust/Pest) ➔ Binary IR (.fxb / .mp) ➔ Host Shell (Compose/SwiftUI)
```

| Feature | Technology | Benefit |
|---------|------------|---------|
| **UI Language** | **Feather UI (.fthr)** | 100% reliable Pest grammar parsing, sub-100ms hot-reloads. |
| **Logic** | **Kotlin/WasmGC** | Native speed for business logic, fully sandboxed. |
| **Protocol** | **FlatBuffers & FlexBuffers** | Zero-copy schema-driven FlatBuffer layout deserialization on mobile hosts. |
| **Bridge** | **MessagePack** | High-speed, binary host-to-core communication. |
| **Ecosystem** | **Universal Plugin SDK** | Dynamic registration of native modules at runtime. |
| **Security** | **CSWSH, Path-Traversal & Cryptographic Verification** | Strict security sandbox, signature verification, and secure boot defense. |

---

## 🛡️ Enterprise-Grade Architectural Hardening

To support mission-critical, enterprise deployments, Feather incorporates four advanced architectural hardening extensions:

### 1. Cryptographic Bundle Verification & Secure Boot
Every dynamically delivered Feather bundle undergoes strict cryptographic signature verification using the high-performance Rust cryptographic engine:
* **Ed25519 Signing**: The CLI (`feather-cli`) signs the compiled bundle's manifest JSON using an Ed25519 private key during creation.
* **Rust-Core Verification**: Verification occurs entirely inside the platform-agnostic Rust `core` module utilizing `ring` (Ed25519 public key checks), preventing platform implementation drift.
* **Secure Boot RSOD**: Mobile hosts (Android Kotlin / iOS Swift) call `verify_manifest_signature` during load. Any mismatch triggers an immediate halt and throws a `CRYPTOGRAPHIC SECURE BOOT FAULT`, rendering a premium glassmorphic crimson Error Boundary dashboard.

### 2. Schema-Driven FlatBuffers Layout Trees
We defined a formal schema for dynamic rendering in `layout.fbs` that replaces standard, heavy JSON parsing with zero-copy binary performance:
* **Layout Compilation**: The CLI serialized layouts directly into FlatBuffers binary frames (`app.feather.fb`).
* **Zero-Copy Decoding**: On Android Compose hosts, the dynamic renderer walks the raw FlatBuffer layout trees with absolute zero allocations on the JVM heap, driving frame drops to absolute zero.

### 3. Incremental Bundle Delta Engine
For rapid local iteration, Feather skips complete application reloading. The dev server uses a Rust core-powered recursive virtual DOM layout diffing engine:
* **Granular Diffing**: Composing old and new JSON UI trees generates targeted patches (`Replace`, `UpdateProperty`, `InsertChild`, `RemoveChild`).
* **In-Place Mutation**: Patches are streamed over WebSockets to Android/iOS clients in real-time, executing localized copy-on-write path traversals under **10ms** to mutate active trees without losing focus, active selections, or application state.

### 4. JIT-Free Uniform WASM execution parity (iOS & Android)
To guarantee execution parity on devices restricted by strict security constraints (such as physical iOS builds which completely ban JIT compiled execution), Feather features:
* **wasmi Interpreter**: A fully sandboxed WebAssembly interpreter that safely executes Compiled Wasm bytecode instructions inside the shared Rust core.
* **Dynamic GcRewriter**: Intercepts complex memory instructions, remapping them to the custom `GcHeap` allocations seamlessly before loading into the interpreter, satisfying platform-level policies without relying on dynamic JIT compilation.

---

## 🛠️ Project Structure

```
feather/
├── core/               Rust logic engine (WasmGC, FlexBuffers, UniFFI)
├── cli/                Rust CLI (Optimizing Compiler, Dev Server, DevTools Dashboard)
├── host/
│   ├── android/        Modular Compose host with FlexBuffers support & SPI Plugin Loader
│   └── ios/            SwiftUI host with MessagePack, Reflection, and Dynamic Plugin Loader
├── sdk/                Kotlin SDK (WasmGC Bindings, Binary MessagePack Encoder)
└── samples/            Reference apps (Netflix Clone, Navigation Demo)
```

---

## Android live demo

[Feather Go and the WhatsApp-style chat demo](docs/phone-demo.md) provide the QR connection page, persistent Android development client, and a Feather SDK app that refreshes without rebuilding its APK. The current chat demo is local; real messaging and physical-device verification remain open.

## Experimental Kotlin application runtime

The [Kotlin/JS runtime guide](docs/kotlin-js-runtime.md) includes a real compiled counter, code refresh, tests, and signed experimental bundles. Select `runtime: "kotlin-js"` in `feather.json`. The native host integration now builds an ARM64 Android debug APK; device execution and iOS build verification remain release gates. [Windows/WSL2 instructions](docs/windows-development.md) cover building and connecting the persistent host.

## Platform delivery plan

See [the detailed platform plan](docs/platform-plan.md) for the no-rebuild development contract, architecture decisions, application capability packs, and production acceptance gates.

## 🚀 Getting Started

### 1. Build the Platform
```bash
# Compile the shared logic engine and the CLI tool
cd cli
cargo build --release
```

### 2. Create a New Project
```bash
feather-cli init --name my-app
cd my-app
```

### 3. Start the Dev Server
```bash
feather-cli start
```
Connect your Android or iOS host shell by scanning the QR code in the terminal or opening `http://localhost:8080/devtools` to debug.

---

## 💎 CLI Reference

| Command | Description |
|---------|-------------|
| `start` | Starts dev server with instant stateful hot-reload and launches `/devtools` dashboard. |
| `build` | Generates an **optimized production bundle** (.fxb/.mp) ready for static delivery. |
| `doctor` | Validates environment, local compiler toolchains, and binary protocol support. |
| `install` | Adds a shared module (Camera, Maps, etc.) to the project configurations. |

---

## 📦 Native Modules & Dynamic Plugins

Feather provides a rich set of native modules out of the box and supports third-party integrations:

- **feather-device-info**: Standard native capabilities provider supplying display scale/density, screen dimensions, OS details, and battery metrics.
- **feather-camera**: High-performance camera preview and capture (CameraX / AVFoundation) featuring safe hardware lifecycle disposal.
- **feather-maps**: Native map viewport (Google Maps / Apple MapKit).
- **Native Bridge**: Automatic, type-safe reflection-based calls to any native platform API.
- **Event Bus**: Reactive native-to-wasm notifications (Lifecycle, Sensors, etc.).

---

## 🔧 Creating & Registering Your Own Native Modules

You can easily extend the platform by building custom native modules that inject Jetpack Compose or SwiftUI views at runtime.

### 1. Write the Native Module

**Android (Kotlin + Jetpack Compose):**
```kotlin
package com.myplugin

import dev.feather.host.FeatherPlugin
import dev.feather.host.FeatherModuleContext
import org.json.JSONObject

class CustomLoggerModule : FeatherPlugin {
    override val name = "my-custom-logger"
    override val version = "1.0.0"

    override fun canHandleLegacyCommand(command: String) = false
    override fun handleLegacyCommand(command: String, context: FeatherModuleContext) = false

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        if (method == "logEvent") {
            val message = payload.optString("message")
            android.util.Log.d("CustomLogger", message)
            context.sendStateAssignment("", "{\"status\":\"success\"}")
            return true
        }
        return false
    }
}
```

**iOS (Swift + SwiftUI):**
```swift
import Foundation

@objc(CustomLoggerModule)
class CustomLoggerModule: NSObject, FeatherPlugin {
    let name = "my-custom-logger"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool { false }
    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool { false }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        if method == "logEvent" {
            let message = payload["message"] as? String ?? ""
            print("CustomLogger: \(message)")
            context.sendStateAssignment("", "{\"status\":\"success\"}")
            return true
        }
        return false
    }
}
```

### 2. Register Your Module

Feather supports both static registration and **dynamic discovery**:

#### Option A: Dynamic Discovery (Zero-Touch)
1. Write a `feather_plugins.json` configuration file:
   ```json
   [
     {
       "name": "my-custom-logger",
       "className": "com.myplugin.CustomLoggerModule"
     }
   ]
   ```
2. **Android**: Place this file in `assets/feather_plugins.json` (or register the class via SPI ServiceLoader `META-INF/services/dev.feather.host.FeatherPlugin`).
3. **iOS**: Add it to your main iOS App Bundle. The Host runtime will automatically locate, instantiate, and register it via class reflection.

#### Option B: Direct Registration (Manual)
Simply add your module directly to the registry in the Host app startup:
* **Android (`MainActivity.kt`)**:
  ```kotlin
  registerOfficialModules()
  moduleRegistry.register(CustomLoggerModule())
  ```
* **iOS (`FeatherDevClient.swift`)**:
  ```swift
  registerOfficialModules()
  moduleRegistry.register(CustomLoggerModule())
  ```

---

## 📖 Documentation

- [Roadmap 2.0](ROADMAP.md) — The path to production readiness.
- [Long-term Architecture](docs/long-term-path.md) — Why Kotlin/Wasm?
- [Platform Contract](docs/platform-contract.md) — Shared runtime rules.
- [Modules](docs/modules.md) — Scaling with the Universal Plugin SDK.


