# Project Feather: Gap Analysis & Missing Components

> [!IMPORTANT]
> While `STATUS.md` marks the project as "100% Complete," this refers to the **Prototype Milestone**. To reach a production-ready "Expo for KMP," the following items are missing or require immediate fixes.

## 1. Android Host Fixes (Urgent)
The Android Host is currently in a "broken" state due to code corruption in `MainActivity.kt`:
- **Syntax Errors**: Lines 230-235 of [MainActivity.kt](file:///d:/feather/host/android/app/src/main/java/dev/feather/host/MainActivity.kt) contain duplicated closing braces and malformed `when` branches.
- **Missing Definitions**: `FeatherNode.CustomNode` is used in the renderer but is not defined in the `FeatherNode` sealed interface.
- **Missing ModuleRegistry**: Unlike the iOS side, the Android side is missing the `ModuleRegistry` class/object entirely, meaning custom native modules cannot be registered.

## 2. Architectural "Drift" (Technical Debt)
The original strategy was to use **Rust** for cross-platform consistency. However:
- **Redundant Logic**: Both Android and iOS have re-implemented the JSON parsing and UI state logic.
- **UniFFI Underutilization**: The [core/src/lib.rs](file:///d:/feather/core/src/lib.rs) contains a sophisticated `AppRuntime` and `BundleManifest` system that is currently **ignored** by the mobile apps.
- **Recommendation**: Update the Host apps to use the UniFFI bindings. The Rust core should be the "Source of Truth" for the UI tree state.

## 3. Tooling & CLI Gaps
- **Stubbed Commands**: 
    - `feather install` only writes to `feather.json`/`feather.lock`. It doesn't actually trigger Maven/Git downloads.
    - `feather doctor` and `feather upgrade` are currently minimal simulations.
- **Parser Limitations**: The [cli/src/main.rs](file:///d:/feather/cli/src/main.rs) uses a manual string scanner (`parse_widgets`) to "parse" Kotlin. This will fail if a user:
    - Uses a variable for a string: `Text(myGreeting)`
    - Uses conditional logic: `if (show) Text("Hi")`
    - Uses standard Kotlin imports or functions.
- **KSP Alignment**: The KSP compiler plugin also uses string scanning. It should be upgraded to use the actual `KSResolver` to build the JSON tree from the real AST.

## 4. Missing Features (Future Roadmap)
- **Asset Pipeline**: The CLI has basic asset copying, but the Host apps don't yet have a system to download and cache remote assets (images/fonts) during the dev loop.
- **Hot Reload vs. Full Refresh**: Currently, the system seems to send the full UI tree on every change. The `BundleDelta` logic in Rust is ready but not integrated into the WebSocket stream.
- **Native Module Bridge**: There are no "official" modules yet (e.g., `feather-storage`, `feather-camera`). We need a standardized template for creating these modules so they can be registered via `feather install`.

## 5. Build Pipeline
- **Cross-Compilation**: There is no automated script to compile the Rust `core` into `.aar` (Android) or `.framework` (iOS). This is the biggest barrier to developers wanting to contribute to the engine.
