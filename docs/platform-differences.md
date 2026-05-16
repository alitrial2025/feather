# Feather Platform Differences

Feather aims for 100% parity between Android and iOS, but some underlying platform differences exist. This document notes them honestly.

## UI Components

| Component | Android (Compose) | iOS (SwiftUI) | Difference |
|-----------|-------------------|---------------|------------|
| `VideoPlayer` | Media3 Exoplayer | AVKit AVPlayer | Minimal. Android supports more containers; iOS is stricter about HLS/MP4. |
| `WebView` | Android WebView | WKWebView | iOS has stricter App Transport Security (ATS) rules; Android is more permissive by default. |
| `TextField` | Material Design | System Default | Visual styling follows platform defaults (Material vs Cupertino) unless explicitly styled. |
| `Dialog` | Material AlertDialog | SwiftUI Alert | iOS alerts are more constrained in layout; Android allows full custom content in dialogs. |

## Layout

- **Safe Areas**: Android handles safe areas via `WindowInsets`; iOS uses `safeAreaInsets`. Feather abstracts this, but behavior at the screen edges may vary slightly in complex nested scrolls.
- **Font Rendering**: Text measuring is performed by the native engine. Line height and character spacing may differ by 1-2 pixels between platforms for the same values.

## Performance

- **WasmGC**: Both platforms use the same `feather-core` Rust library for WASM execution. Android typically uses an ARM64 JIT if available; iOS is interpreted due to JIT restrictions in some environments.
- **Rendering**: Compose and SwiftUI are both hardware-accelerated. Compose is generally more flexible with dynamic tree updates, while SwiftUI is more optimized for system consistency.

## Modules

- **Location**: Android requires `ACCESS_FINE_LOCATION`; iOS requires `NSLocationWhenInUseUsageDescription` in `Info.plist`.
- **Haptics**: iOS provides highly specific `UIImpactFeedbackGenerator` styles; Android vibrators vary greatly by hardware manufacturer.

## Conclusion

Apps should avoid relying on pixel-perfect parity. Instead, rely on **functional parity**. A button should trigger the same action, and a list should show the same data, but they may look and feel "at home" on their respective platforms.
