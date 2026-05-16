# Feather 🪶

**Feather** is a next-generation development platform for Kotlin Multiplatform (KMP) that provides an "Expo Go" experience — zero-Gradle UI iteration with truly native rendering.

## How It Works

```
Developer writes Kotlin          CLI parses & streams JSON         Host renders native UI
┌─────────────────────┐          ┌─────────────────────┐          ┌─────────────────────┐
│  App.kt             │  ─────▶  │  feather start      │  ─────▶  │  Feather Host App   │
│  Column(            │  file    │  WebSocket server   │  ws://   │  Jetpack Compose /  │
│    Text("Hello"),   │  watch   │  AST → JSON         │  JSON    │  SwiftUI            │
│    Button(...)      │          │  State management   │          │  ModuleRegistry     │
│  )                  │          │  Bundle system      │          │  Dynamic renderer   │
└─────────────────────┘          └─────────────────────┘          └─────────────────────┘
```

## Technical Stack

| Layer      | Technology        | Purpose                              |
|------------|-------------------|--------------------------------------|
| **Core**   | Rust + UniFFI     | State management, protocol, bundling |
| **CLI**    | Rust (clap/warp)  | Dev server, file watcher, bundler    |
| **SDK**    | Kotlin (KMP)      | UI primitives, module contracts      |
| **Host**   | Compose / SwiftUI | Dynamic native rendering             |
| **Compiler** | KSP            | Kotlin → Feather IR generation       |

## Project Structure

```
feather/
├── core/               Rust logic engine (UniFFI bindings)
├── cli/                Rust CLI (dev server, bundler, package manager)
├── host/
│   ├── android/        Jetpack Compose host app
│   └── ios/            SwiftUI host app
├── sdk/                KMP UI primitives & module contracts
├── compiler/ksp/       KSP processor (Kotlin → Feather IR)
├── samples/
│   ├── hello-world/    Sample Feather project
│   └── modules/        Sample native module (feather-camera)
├── scripts/            Build scripts for cross-compilation
└── docs/               Architecture & API documentation
```

## Quick Start

### 1. Run the CLI Dev Server
```bash
cd cli
cargo run -- start --project ../samples/hello-world
```

### 2. Connect the Host App
- **Android Emulator**: connects to `ws://10.0.2.2:8080/ws`
- **iOS Simulator**: connects to `ws://127.0.0.1:8080/ws`

### 3. Edit & Watch
Modify `samples/hello-world/src/.../App.kt` — the host app updates instantly.

## CLI Commands

| Command | Description |
|---------|-------------|
| `feather start` | Start the dev server with file watching |
| `feather init <name>` | Scaffold a new Feather project |
| `feather install <module>` | Install a native module |
| `feather doctor` | Check project configuration |
| `feather modules` | List installed modules |
| `feather upgrade` | Bump SDK and module versions |

## SDK 1.0 Widgets

| Widget | Description |
|--------|-------------|
| `Text` | Static text |
| `StateText` | Reactive text from CLI state |
| `Button` | Native button with action event |
| `TextInput` | Native text field with change events |
| `Image` | Remote or local image |
| `Spacer` | Flexible empty space |
| `Column` | Vertical layout container |
| `Row` | Horizontal layout container |

## Native Modules

Feather supports custom native modules that extend the Host app with platform-specific widgets. See [docs/modules.md](docs/modules.md) for the full creation guide.

```bash
feather install feather-camera
```

## Documentation

- [Protocol](docs/protocol.md) — WebSocket render & action protocol
- [SDK 1.0](docs/sdk-1.0.md) — Widget API and runtime contract
- [Bundler v0](docs/bundler-v0.md) — Bundle format and manifest
- [Modules](docs/modules.md) — Creating native modules

# feather
