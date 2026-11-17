# Experimental Kotlin/JS runtime

## What is working

A real Kotlin compiler compiles the shared SDK and a counter application. The
output executes in QuickJS inside `feather-core` without JIT. Kotlin code builds
the render tree and handles actions. This path does not extract widgets from
source text. `runtime: "kotlin-js"` selects it; existing projects keep their
previous behavior.

The full integration test changes Kotlin logic from increment-by-one to
increment-by-two while the CLI remains running. Count is preserved across the
new interpreter instance. Invalid Kotlin leaves the last compiled artifact
untouched; initialization/render failures leave the last good runtime and
state intact. Correcting an edit restores a full render snapshot.

This is an experimental compiler/runtime slice. The CLI supports a server-side
preview; native hosts also have signed code loading and refresh hooks. Android
compiles, its decoder contract test passes, and an ARM64 debug APK packages the
actual runtime. Kotlin/JNA checks execute the compiled app through the native
library and preserve independent client state across signed code refresh.
Physical-device execution is not yet verified; the iOS CI build has not yet
passed. See [Windows development](windows-development.md).

## Try the sample

Requirements: Rust, a C compiler (QuickJS), Python 3, JDK 21 (validated), and HTTPS
access to GitHub release assets and Maven Central on first compilation.

```sh
cargo build --locked --manifest-path cli/Cargo.toml
export FEATHER_TOOLS_HOME=/workspace/.feather-tools  # choose a writable cache
cli/target/debug/feather-cli compile-js --project samples/runtime-counter
cli/target/debug/feather-cli start --project samples/runtime-counter
```

Use the executable under `$CARGO_TARGET_DIR/debug` if that variable is set. Edit
`App.kt`; the compiler rebuilds the Kotlin/JS bundle, then the server loads the
new code while retaining compatible state. Ordinary edits do not invoke a
native application build. The first compiler run downloads checksum-pinned
Kotlin 2.1.10 and serialization 1.8.0 artifacts. Later unchanged builds validate
the input/output content hashes and reuse the compiled result. Changed inputs
currently use the full compiler, not an incremental daemon: do not expect
instantaneous logic refresh yet.

`FEATHER_REPOSITORY_ROOT` can point the CLI to the repository containing the
compiler driver and SDK; otherwise it uses its build-time repository location.
The compiler currently includes the project's Kotlin files and Feather's shared
and JS SDK sources. External KMP library resolution and a standalone installed
CLI distribution are future work.

## Repeatable checks

```sh
cargo test --locked --manifest-path core/Cargo.toml
cargo test --locked --manifest-path cli/Cargo.toml
cargo build --locked --manifest-path cli/Cargo.toml
python3 scripts/smoke_cli.py cli/target/debug/feather-cli
python3 scripts/smoke_kotlin_js.py cli/target/debug/feather-cli
```

The Kotlin integration test starts and stops its own CLI in a temporary project.
It tests compilation, cached builds, rendering, action execution, live logic
changes, state retention, compiler error/recovery, and manifest-listed JavaScript
hashes. CI runs both smoke scripts. CI configuration is added; local passing
checks do not establish a completed remote CI run.

To execute a compiled application directly with no development server:

```sh
cargo run --locked --manifest-path core/Cargo.toml --bin feather-js-check -- samples/runtime-counter/.feather/app.js
```

The check prints its initial render and render after an `increment` action.
This exercises the native core interpreter, not Android or iOS UI. Core also
exports `load_signed_javascript` for host integration: it authenticates the
manifest with an explicitly supplied trusted public key, verifies protocol/SDK
and JavaScript hash/length, then stages the application before promotion.

## Bundle output

```sh
cli/target/debug/feather-cli build --project samples/runtime-counter --target android
```

This creates an **experimental signed runtime bundle**, not an APK/IPA, under
`dist/android/bundles/<manifest-hash>/`. `ios` selects the corresponding output
folder. Artifacts are checked before staging, JavaScript is included in the
signed manifest/delta model, and release manifests omit development source
references/deltas. Verify a generated bundle with an independently configured trusted key:

```sh
cargo run --locked --manifest-path core/Cargo.toml --bin feather-bundle-check -- <bundle-directory> <trusted-public-key-file>
```

The key must come from trusted app configuration, not the downloaded bundle.
The integration test verifies a good package and rejects a modified JavaScript
artifact. Final native shell integration, signing and installation remain
release gates. Do not deploy these bundles as production mobile apps.

## ABI and safety boundaries

- The compiler calls `installApp(app)` from the Kotlin entry point.
- The SDK installs `__featherRender` and `__featherAction` callbacks.
- Core provides state, log and queued native module bridges. Render-time SDK
  descriptors are stable; the core assigns request IDs on dispatch. Tests cover
  declarative module dispatch, typed simulated results and native event delivery.
  Platform module behavior, cancellation and coroutine scheduling still need
  broader implementation and device coverage.
- Compatible reloads retain typed shared state. App-local Kotlin variables are
  recreated. Application `onCreate` must initialize defaults only when missing.
- Reload initialization runs against staged state/queues; promotion happens
  only after a valid first render. Identical code reuses the interpreter.
- QuickJS has a 128 MiB heap limit, a 1 MiB stack limit, and execution deadlines
  (five seconds initialization, one second per render/action). These are initial
  development defaults, not measured mobile performance budgets. Bridge limits
  also bound shared state (16 MiB), log entries and queued module requests.
- Promise/timer jobs, background services, general app state migrations and
  device profiling remain unimplemented/unverified. Lifecycle events have a core
  bridge, but full platform behavior is not device-validated.
- Native hosts use app-configured public keys, verified JavaScript loading and
  regenerated bindings. Android's Gradle property is `featherBundlePublicKey`;
  iOS uses `FeatherBundlePublicKey` in Info.plist. Missing/invalid keys reject code.
  Camera/map sample plugins remain opt-in and are not default host capabilities.

## Why this backend exists

The pinned official Kotlin WASI compiler successfully produced a 465 KiB
`increment` probe. Loading it in Feather failed with `CallRef` in the rewriter
and unsupported GC heap types in wasmi. Replacing unknown instructions with
`unreachable` is not a compatibility solution; the rewriter now reports errors.
The `feather-wasm-check` core binary can reproduce runtime loading diagnostics
for real compiler output. Kotlin/WASM remains a research path; Kotlin/JS plus a
no-JIT interpreter provides a separately testable route to the product workflow.
