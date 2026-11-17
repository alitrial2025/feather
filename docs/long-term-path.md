> Status update (2026-11-17): the real Kotlin 2.1.10 WASI probe fails in the
> current interpreter with unsupported GC heap types and `CallRef`. The rewriter
> is experimental and now rejects unsupported instructions. The working
> compiler/runtime proof uses an opt-in Kotlin/JS backend with QuickJS. See
> [Kotlin/JS runtime](kotlin-js-runtime.md) and [platform plan](platform-plan.md).
> The original design below is an aspiration, not validated Kotlin/WASM parity.

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

Using the official compiler avoids maintaining a parallel Kotlin parser, but language, standard-library and runtime compatibility must be verified against actual compiler output. General Kotlin/WASM execution has not been established.