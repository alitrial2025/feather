# Feather 🪶

**Feather** is a next-generation high-performance native delivery platform for Kotlin Multiplatform (KMP). It enables developers to build truly native apps with Kotlin that are delivered dynamically to host shells on Android and iOS with near-instant hot-reloads and zero-copy binary performance.

## 🚀 Key Features

- **Zero-Gradle UI Iteration**: Update your app's UI and logic instantly over the air using the Feather UI (FUI) language.
- **Truly Native**: High-performance rendering using Jetpack Compose (Android) and SwiftUI (iOS).
- **Binary Protocol (Zero-Copy)**: Powered by **FlexBuffers** and **MessagePack** for ultra-fast, memory-efficient UI updates.
- **Full Kotlin Support**: Authoritative logic resides in a robust **WasmGC** runtime, supporting the full power of Kotlin.
- **Bridge Factory**: Automatically scale to thousands of native APIs via our dynamic reflection bridge.
- **Universal Contract**: One UI codebase, identical behavior on all platforms.

## 🏗️ Architecture: The High-Performance Pivot

Feather 2.0 has moved beyond simple JSON rendering to a professional-grade binary pipeline:

```
Source (.fthr / .kt) ➔ FUI Compiler (Rust/Pest) ➔ Binary IR (.fxb / .mp) ➔ Host Shell (Compose/SwiftUI)
```

| Feature | Technology | Benefit |
|---------|------------|---------|
| **UI Language** | **Feather UI (.fthr)** | 100% reliable parsing, sub-100ms hot-reloads. |
| **Logic** | **Kotlin/WasmGC** | Native speed for business logic, fully sandboxed. |
| **Protocol** | **FlexBuffers** | Zero-copy deserialization; host reads IR directly from memory. |
| **Bridge** | **MessagePack** | High-speed, binary host-to-core communication. |
| **Ecosystem** | **Bridge Factory** | Automated access to any Android/iOS API via reflection. |

## 🛠️ Project Structure

```
feather/
├── core/               Rust logic engine (WasmGC, FlexBuffers, UniFFI)
├── cli/                Rust CLI (Optimizing Compiler, Bundler, Dev Server)
├── host/
│   ├── android/        Modular Compose host with FlexBuffers support
│   └── ios/            SwiftUI host with MessagePack & Native Reflection
├── sdk/                Kotlin SDK (WasmGC Bindings, Binary MessagePack Encoder)
└── samples/            Reference apps (Netflix Clone, Navigation Demo)
```

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
Connect your Android or iOS host shell by scanning the QR code in the terminal.

---

## 💎 CLI Reference

| Command | Description |
|---------|-------------|
| `start` | Starts dev server with instant hot-reload. |
| `build` | Generates an **optimized production bundle** (.fxb/.mp). |
| `doctor` | Validates environment and binary protocol support. |
| `install` | Adds a shared module (Camera, Maps, etc.) to the project. |

---

## 🎥 Ecosystem (Phase 2)

Feather provides a rich set of native modules out of the box, all optimized for the binary protocol:

- **Camera**: High-performance preview and capture (CameraX / AVFoundation).
- **Maps**: Native map integration (Google Maps / MapKit).
- **Native Bridge**: Automatic access to any platform API via `Native.call()`.
- **Event Bus**: Reactive native-to-wasm notifications (Lifecycle, Sensors, etc.).

---

## 📖 Documentation

- [Roadmap 2.0](ROADMAP.md) — The path to production readiness.
- [Long-term Architecture](docs/long-term-path.md) — Why Kotlin/Wasm?
- [Platform Contract](docs/platform-contract.md) — Shared runtime rules.
- [Modules](docs/modules.md) — Scaling with the Bridge Factory.

# feather
