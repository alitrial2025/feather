# Creating Feather Native Modules

This guide explains how to create a native module that extends the Feather Host app with custom UI widgets.

## Architecture

```
┌──────────────┐     JSON      ┌──────────────┐
│  Feather CLI │ ──────────▶   │  Host App    │
│  (Rust)      │  WebSocket    │  (Android/   │
│              │               │   iOS)       │
└──────────────┘               └──────┬───────┘
                                      │
                               ModuleRegistry
                                      │
                        ┌─────────────┼─────────────┐
                        │             │             │
                   FeatherCamera  FeatherMaps  FeatherAuth
```

Native modules run **inside the Host app** and provide custom Composable/SwiftUI views that the dynamic renderer injects when it encounters a matching node type.

## Step 1: Define the Module (KMP Shared)

```kotlin
// feather-camera/src/commonMain/kotlin/.../CameraModule.kt
package dev.feather.modules.camera

import dev.feather.sdk.FeatherModule

class CameraModule : FeatherModule {
    override val name = "feather-camera"
    override val version = "1.0.0"
    override fun widgets(): List<String> = listOf("CameraPreview", "PhotoPicker")
}
```

## Step 2: Android Implementation

Create a class implementing `FeatherModule` from the Host:

```kotlin
// feather-camera/android/CameraModuleAndroid.kt
class CameraModuleAndroid : dev.feather.host.FeatherModule {
    override val name = "CameraPreview"

    @Composable
    override fun Render(json: JSONObject, onAction: (String, String?) -> Unit) {
        // Your native Compose UI here
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            Text("Camera Preview")
        }
    }
}
```

Register it in `MainActivity.onCreate()`:

```kotlin
ModuleRegistry.register(CameraModuleAndroid())
```

## Step 3: iOS Implementation

Create a struct conforming to `FeatherModule`:

```swift
// feather-camera/ios/CameraModuleIOS.swift
struct CameraModuleIOS: FeatherModule {
    let name = "CameraPreview"

    func render(dict: [String: Any], onAction: @escaping (String, String?) -> Void) -> AnyView {
        AnyView(
            Rectangle()
                .fill(Color.gray)
                .frame(height: 300)
                .overlay(Text("Camera Preview").foregroundColor(.white))
        )
    }
}
```

Register it in the app startup:

```swift
ModuleRegistry.shared.register(CameraModuleIOS())
```

## Step 4: CLI Integration

The module emits nodes with a custom `type` field. In the developer's `App.kt`:

```kotlin
// The CLI parser detects unknown widget types and emits them with
// a "module" field so the Host knows which module to load.
```

The CLI's bundler automatically discovers module requirements from the render payload and lists them in `manifest.json`.

## Step 5: Distribution

```bash
# Users install your module via:
feather install feather-camera

# This updates feather.json:
{
  "modules": {
    "feather-camera": "1.0.0"
  }
}

# And generates feather.lock with resolved coordinates:
{
  "resolved": {
    "feather-camera": {
      "version": "1.0.0",
      "android": "dev.feather:camera:1.0.0",
      "ios": "https://github.com/feather-modules/camera.git"
    }
  }
}
```

## Module Lifecycle

1. **Install** — `feather install` adds the module to `feather.json` and `feather.lock`
2. **Bundle** — The CLI detects module node types in the render payload and includes them in the bundle manifest
3. **Resolve** — The Host app checks the manifest's `modules` array and verifies all required modules are registered
4. **Render** — When the renderer encounters an unknown node type, it delegates to `ModuleRegistry.get(type)`
5. **Action** — Module views can send action events back to the CLI via the `onAction` callback

## Reference Implementation

See `samples/modules/feather-camera/` for a complete working example.
