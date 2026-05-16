# Feather Long-Term Architecture Path

After evaluating the limits of KSP and the fragile nature of string-scanning parsers for extracting UI trees, we have made a definitive decision on the long-term architectural path for Feather.

## The Decision: Kotlin/WASM Runtime

Feather will adopt a **Kotlin/WASM runtime** as its primary source of truth for both UI structure and app logic.

### Why not KSP IR Generation?

Kotlin Symbol Processing (KSP) is fantastic for declarations and types, but it explicitly **does not provide access to function bodies**. To parse a `render()` method, we are forced to treat the file as a raw string and use regex/heuristics to find `Text()`, `Column()`, etc. This breaks down when developers use standard Kotlin features like `if/else`, loops, variables, and imported custom components. Expanding KSP to use full `kotlin-compiler-embeddable` AST parsing is too heavy and brittle across Kotlin versions.

### The Kotlin/WASM Solution

1. **Compilation**: The Feather app is compiled using the official Kotlin/WasmGC compiler backend.
2. **Execution**: The `feather-core` Rust library runs the resulting `.wasm` binary using a WasmGC-compatible engine (e.g., Wasmi with GC rewrites).
3. **UI Extraction**: The app's `render()` method runs natively in WASM, producing a memory-resident representation of the UI tree. The Rust core extracts this tree via FFI (host calls) and converts it to the standard `app.feather.json` render payload.
4. **Action Handling**: When the user taps a button, the host sends the action to the Rust core, which calls back into the WASM instance's `handleAction()` function. The WASM instance updates its state and triggers a re-render.

### Role of the Fallback Parser

The string-scanning parsers (CLI and KSP) will be kept **strictly as a development escape hatch**. They allow for extremely fast hot-reloading of simple static UI changes during development, bypassing the multi-second Kotlin/WASM compilation step. However, they are not the authoritative source of truth.

## Conclusion

By relying on standard Kotlin/WASM compilation, Feather supports 100% of the Kotlin language out of the box—loops, conditionals, custom components, and complex state management—without needing to maintain a buggy parallel Kotlin parser.