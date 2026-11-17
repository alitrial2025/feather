# Production readiness review

Reviewed 2026-11-17. Feather is an experimental platform with a working Rust
core/CLI development path. It is not yet a production-ready mobile delivery
platform. Earlier completion labels in README, STATUS, and ROADMAP describe
intent or implementation presence; they are not evidence of release validation.
A percentage would conceal major unverified dependencies.

## Delivery plan and current milestone

See [the platform delivery plan](platform-plan.md) for the development rebuild
contract, compiler/runtime decisions, application capability packs and release
gates. The first refresh milestone now includes filtered/debounced filesystem
watching, blocking watcher isolation, and full snapshot recovery after invalid
edits. The real WebSocket smoke check verifies source edits without restarting
the server, typed state preservation, syntax-error recovery and reconnect.
The opt-in [Kotlin/JS runtime](kotlin-js-runtime.md) now provides a real compiled
counter and logic refresh in the CLI/core without JIT. Its integration check
preserves state across changed Kotlin action code and rejects compiler errors.
Native host source now integrates verified code loading and refresh. Android
Kotlin compilation, its decoder unit test and ARM64 APK packaging pass. Physical
device execution is unverified; iOS CI must still run successfully.

## Verified in the Linux cloud environment

- Core unit tests exercise state, layout differences, GC, WASM execution and
  binary layout serialization.
- CLI tests exercise FUI parsing, symbolic parsing, bundle signatures and
  rejection of unauthorized browser origins.
- `scripts/smoke_cli.py` launches the real CLI and checks DevTools, bundle
  manifest, render content, SHA-256/byte count, WebSocket origin rejection, live
  source edits, typed state retention, error recovery and reconnect.
- Manifest signing verifies with the generated project key; changing a signed
  artifact hash invalidates the signature.
- The WASM compiler command still fails explicitly. A real Kotlin WASI probe
  was compiled and failed runtime compatibility; unsupported rewriter
  instructions now produce errors rather than silently becoming traps.
- Experimental Kotlin/JS compilation, core execution, action changes, state
  retention, compiler-error recovery and signed runtime bundle packaging are
  verified by `scripts/smoke_kotlin_js.py`. This produces no APK/IPA.
- Linux shared/static library builds and Kotlin/Swift UniFFI binding generation
  succeed. Core also exports signed JavaScript loading with signature, hash,
  runtime/SDK checks and staged promotion. These do not establish Android/iOS
  build or device compatibility.

## Release gates (in dependency order)

| Gate | Current evidence / gap | Required acceptance check |
| --- | --- | --- |
| Reproducible Rust platform | Local build/tests pass; Cargo lockfiles tracked; CI includes Kotlin checks | CI must pass on a clean runner; pin release toolchains |
| Kotlin compiler | Experimental Kotlin/JS renders/handles actions in core; real WASI output fails GC compatibility | Add incremental compilation, async/module/lifecycle tests, then execute the same compiled app on devices |
| Host builds | Android wrapper, ARM64 Rust build and debug APK pass; generated bindings validated through Kotlin/JNA; iOS build configuration added | Install/exercise Android on a device; pass hosted macOS build and simulator/device tests |
| Bundle trust | Hosts use configured pinned keys and core verification; tamper tests pass; debug APK includes a pinned sample key | Device trust/error tests, development pairing, key rotation and rollback protection; never bootstrap trust from a downloaded bundle |
| UI protocol | Rust binary writer fixed and regression tested; native decoding not exercised | Same golden layout decoded by Rust, Android, and iOS; verify scalar defaults, children, styles and all supported widgets |
| Live updates | Rust diff tests exist; native patch/state behavior unverified | Device tests for reload, state preservation, reconnect, property deletion and nested navigation |
| Native modules | Some APIs are placeholders (including location); registration does not prove functionality | Capability matrix, explicit unsupported errors, permission denial and lifecycle tests on both platforms |
| Production packaging | Kotlin/JS can produce a signed runtime bundle; verifier checks trust key and artifact hashes | Native app packaging, schema/ABI checks, atomic device install and rollback; no production deployment claim |
| Network boundary | Browser origin parsing hardened; no-origin native clients remain supported | Treat CLI as a development service; require authenticated transport and a separate threat review before any remote production exposure |

## Development checks

From the repository root, with Rust available:

```sh
cargo test --manifest-path core/Cargo.toml
cargo test --manifest-path cli/Cargo.toml
cargo build --manifest-path cli/Cargo.toml
python3 scripts/smoke_cli.py cli/target/debug/feather-cli
```

When `CARGO_TARGET_DIR` is set, pass that directory's `debug/feather-cli` to the
smoke script instead. It uses a temporary project and terminates its own server.
No native mobile, Kotlin compiler, production deployment, latency, memory, or
load-testing claims follow from these checks.

Next implementation milestone: run the hosted iOS compile pipeline, install
and exercise the Android APK, then test the native refresh path on devices. Async scheduling, native event/module
round trips, compiler latency and production packaging remain release gates.
Do not add more advertised modules before this vertical path works.
