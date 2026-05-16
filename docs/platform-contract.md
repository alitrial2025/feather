# Feather Platform Contract

This document defines the shared contract between Feather apps, SDK, compiler, CLI, core runtime, native hosts, and native modules.

Feather is a general native app platform. It must support media, ecommerce, mobility, productivity, social, finance, and custom business apps from the same app model.

## Core Principle

App code imports Feather APIs. Native implementation details stay inside the Feather host and modules.

Correct:

```kotlin
import dev.feather.sdk.*

VideoPlayer(src = videoUrl)
Button(text = "Checkout", action = Payments.presentCheckout(...))
```

Incorrect:

```kotlin
import androidx.media3.exoplayer.ExoPlayer
import android.webkit.WebView
```

The app describes intent. The host performs the native work.

## Runtime Layers

1. SDK: Kotlin APIs and serializable widget/module models.
2. Compiler: converts app code into Feather IR, render JSON, or WASM runtime artifact.
3. CLI: serves bundles, watches files, validates manifests, resolves modules, and streams updates.
4. Core: owns state, actions, diagnostics, module calls, async results, and bundle metadata.
5. Host: renders native UI and implements native modules.
6. Modules: platform capabilities exposed through a stable registry.

## Render Message

The host receives render messages from the CLI or local bundle.

```json
{
  "type": "render",
  "protocol_version": 1,
  "root": {
    "type": "Text",
    "text": "Hello"
  },
  "state": {},
  "diagnostics": []
}
```

Required fields:

- `type`: `render`, `render_error`, or future protocol message type.
- `protocol_version`: integer protocol version.
- `root`: root render node.

Optional fields:

- `state`: current resolved runtime state snapshot.
- `diagnostics`: compiler/runtime diagnostics.
- `required_modules`: module requirements if not already supplied by bundle manifest.

## Render Nodes

Every render node must have:

- `type`: stable node type.

Render nodes may have:

- `id`: stable node ID.
- `style`: serializable style object.
- `children`, `items`, `child`, or typed node-specific fields.
- `module`: module name when the node is provided by a module.
- `moduleVersion`: requested module version.

Example module node:

```json
{
  "type": "MapView",
  "module": "feather-maps",
  "moduleVersion": "1.0.0",
  "props": {
    "latitude": -1.286389,
    "longitude": 36.817223,
    "zoom": 14
  }
}
```

## Actions

User interaction flows from host to core/CLI as an action event.

```json
{
  "type": "action",
  "action": "cart.add",
  "payload": {
    "productId": "sku_123"
  }
}
```

Rules:

- `action` is a stable string identifier.
- `payload` must be JSON-serializable.
- Hosts should not parse app business logic.
- Core decides whether the action updates state, calls WASM, or invokes a module.

## Module Calls

Module calls must use structured requests. Raw string commands are legacy compatibility only.

```json
{
  "type": "module_call",
  "id": "call_01HX",
  "module": "feather-network",
  "method": "request",
  "payload": {
    "method": "GET",
    "url": "https://api.example.com/products",
    "headers": {}
  },
  "resultStateKey": "products"
}
```

Required fields:

- `type`: `module_call`.
- `id`: unique call ID.
- `module`: module package name.
- `method`: module method.
- `payload`: JSON object.

Optional fields:

- `resultStateKey`: state key to receive success result.
- `errorStateKey`: state key to receive error result.
- `timeoutMs`: host-side timeout hint.

## Module Results

Successful async result:

```json
{
  "type": "module_result",
  "id": "call_01HX",
  "ok": true,
  "result": {
    "status": 200,
    "body": "..."
  }
}
```

Failed async result:

```json
{
  "type": "module_result",
  "id": "call_01HX",
  "ok": false,
  "error": {
    "code": "network.timeout",
    "message": "Request timed out",
    "recoverable": true
  }
}
```

Rules:

- Module results must preserve call ID.
- Errors must have stable `code`.
- Human text belongs in `message`.
- Core writes results back into state and triggers render.

## Module Registry

Hosts must expose a module registry.

Required registry behavior:

- Register official modules at startup.
- Register third-party modules when bundled into the host.
- Resolve module by name.
- Check semver compatibility.
- Report missing module diagnostics.
- Report unsupported platform diagnostics.
- Route module calls to implementation.
- Route module render nodes to implementation.

Android shape:

```kotlin
interface FeatherModule {
    val name: String
    val version: String
    fun canRender(type: String): Boolean
    fun call(method: String, payload: JSONObject, callback: FeatherModuleCallback)
}
```

iOS shape:

```swift
protocol FeatherModule {
    var name: String { get }
    var version: String { get }
    func canRender(type: String) -> Bool
    func call(method: String, payload: [String: Any], callback: FeatherModuleCallback)
}
```

## Official Module Set

These modules are part of the Feather standard platform.

| Module | Purpose |
| --- | --- |
| `feather-network` | HTTP, uploads, downloads, cancellation |
| `feather-storage` | key-value, JSON, secure storage |
| `feather-navigation` | routes, stacks, params, deep links |
| `feather-media` | video, audio, sessions, captions |
| `feather-webview` | embedded web content and JS bridge |
| `feather-image` | remote/local images, caching |
| `feather-device` | haptics, clipboard, share, device info |
| `feather-files` | app files, pickers, downloads |
| `feather-auth` | biometric and OAuth flows |
| `feather-notifications` | local/push notifications |
| `feather-location` | current/watch location |
| `feather-camera` | capture, picker, preview, scanning |
| `feather-maps` | map view, markers, routes |
| `feather-payments` | payment provider abstraction |

## Bundle Manifest

Bundles must describe the app and required runtime capabilities.

```json
{
  "name": "shop",
  "bundle_version": 0,
  "sdk_version": "1.0.0",
  "protocol_version": 1,
  "render": {
    "path": "app.feather.json",
    "sha256": "...",
    "bytes": 1234
  },
  "modules": [
    {
      "name": "feather-network",
      "version": "1.0.0"
    }
  ],
  "permissions": [
    "network"
  ],
  "assets": [],
  "dependencies": [],
  "delta": {
    "changed": [],
    "removed": []
  }
}
```

Rules:

- Hosts must validate `sdk_version` and `protocol_version`.
- Hosts must validate module availability before rendering module nodes.
- Production bundles must include checksums.
- Future production bundles should be signed.

## Permissions

Modules declare permissions in manifest metadata.

Examples:

- `network`
- `camera`
- `microphone`
- `location.foreground`
- `location.background`
- `notifications`
- `photos.read`
- `photos.write`
- `files.read`
- `files.write`
- `biometric`

Rules:

- App code should not request platform permissions directly.
- Modules request permissions through host permission routing.
- Missing permissions should produce diagnostics, not silent failure.

## Compatibility

Every runtime piece has a version:

- SDK version
- protocol version
- CLI version
- core version
- host version
- module version

Rules:

- CLI should reject incompatible SDK/protocol combinations.
- Host should reject incompatible bundle protocol versions.
- Module registry should reject incompatible module versions.
- `feather doctor` should explain compatibility problems before runtime.

## Legacy Compatibility

Current SDK APIs use string commands like:

```kotlin
"host:Network/get={...}"
```

This remains legacy compatibility during migration only.

Migration path:

1. Keep existing string commands working.
2. Add structured module request models.
3. Teach core to handle structured requests.
4. Move host implementations behind module registry.
5. Deprecate direct string command parsing.

## Implementation Priority

1. Android `ModuleRegistry`.
2. `feather-network` and `feather-storage` as first official modules.
3. `feather-media` for `VideoPlayer` and `AudioPlayer`.
4. `feather-webview`.
5. SDK typed module request models.
6. CLI `doctor` compatibility checks.
7. KSP/compiler contract alignment.
8. iOS parity.

