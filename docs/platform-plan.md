# Feather platform delivery plan

## Product goal

Build a general-purpose native app framework competitive with React Native's
development loop and extensibility: install a host once, write Kotlin, save,
and see UI and application logic refresh on a connected device. Release builds
package a tested app runtime and native capabilities for store distribution.

Music/video streaming, chat, social feeds, productivity, commerce, and mobility
are target workloads. Framework support does not supply their backend services,
content licences, payment agreements, encryption design, moderation, or global
infrastructure. A native extension escape hatch is essential: a fixed catalogue
of widgets cannot support every possible application or hardware capability.

## The rebuild contract

| Change | Development behavior | Native app rebuild? |
| --- | --- | --- |
| Kotlin UI, business logic, styles, assets | Incrementally compile changed code on the developer machine, transfer a versioned update, refresh compatible state | No |
| State schema or incompatible code change | Explain why and perform a controlled app-runtime reload | No |
| Native module already installed in host | Validate declared capability/version and load code using its typed API | No |
| New native dependency, entitlement, permission declaration, platform SDK or runtime ABI | Regenerate development host and reconnect | Yes |
| Release signing, app identity, icons, store metadata, final binary | Reproducible release build and device tests | Yes |

“No rebuild on every edit” does not mean no compilation: arbitrary Kotlin logic
requires a real incremental compiler. The current symbolic Kotlin parser and
FUI parser are transitional UI tools, not a Kotlin language implementation.

## Architecture decisions

1. Keep Kotlin as the primary application language. Use a persistent native
   development host (Compose/SwiftUI) with the Rust runtime and installed modules.
2. Make SDK, compiler, core and both hosts share one versioned contract: node
   identity, state, actions, async module calls, errors, permissions and assets.
3. Keep FUI useful for layout iteration and runtime testing. Do not expand source
   scanning to impersonate support for arbitrary Kotlin semantics.
4. Prove a Kotlin-to-interpreted-WASM ABI before committing to a WASI or WasmGC
   backend. Kotlin GC, coroutines, strings, exceptions, memory and module imports
   must be tested together. The existing rewriter is a prototype, not proof of
   general Kotlin bytecode compatibility. iOS execution must work without JIT.
5. Run app logic on the device in the final architecture. The dev server compiles
   and delivers code; it must not be required for production logic execution.
6. Use typed, asynchronous, cancellable module requests with request IDs,
   lifecycle ownership, streaming events, structured errors and capability checks.
   Bulk media must not be copied through JSON state or the render tree.
7. Separate development transport from signed production delivery. Host trust
   keys are app configuration. Updates require compatibility checks, atomic
   activation, artifact verification, recovery and rollback.

## Delivery sequence and exit criteria

### 0. Establish a truthful, repeatable baseline

Dependencies: none. Fix builds; protect binary/protocol compatibility; run core,
CLI and real-server checks in CI. Record unsupported capabilities explicitly.
Exit: a fresh runner builds and checks the documented workflow. Android/iOS
claims remain unverified until their own builds/tests pass.

### 1. Reliable edit-and-refresh loop (implementation started)

- Watch source/assets and configured compiler outputs; debounce editor saves.
- Ignore generated bundles, keys, logs, reads and unrelated cache writes.
- Keep watcher work off async workers; report watcher failures.
- Keep typed application state on compatible UI changes; do not mutate the
  previous good layout baseline into an error screen.
- Deliver diagnostics for invalid edits, then recover after a correction.
- Test edits over a real WebSocket without restarting the server. Check fresh
  connections, stable state, syntax-error recovery and no refresh feedback loop.
- Next: versioned revisions, client acknowledgements, explicit full resync after
  lag/reconnect, stable node identity, navigation preservation and asset updates.

Exit: the existing UI path reliably updates without a native build. This is not
full Kotlin logic refresh or a validated device workflow yet.

### 2. Compiler/runtime proof: one real Kotlin application (experimental slice working)

Current evidence: a Kotlin 2.1.10 WASI probe compiles but cannot execute through
the GC rewriter/interpreter. An opt-in Kotlin/JS + QuickJS backend now compiles
and executes a real SDK counter, refreshes changed Kotlin logic while retaining
state, and packages signed runtime artifacts. The native host source now loads signed code; Android builds an ARM64 debug APK
and Kotlin/JNA verifies local execution and refresh. Physical devices and iOS
build success are still unverified. See [the runtime guide](kotlin-js-runtime.md). The milestones below
remain acceptance requirements, not claims fulfilled by the proof.

- Freeze a small ABI specification and a capability test corpus first.
- Pin Kotlin and build tooling; provide a wrapper and deterministic sample build.
- Compile a counter with branching, data models and async results; execute it
  with the no-JIT runtime. Add string, collections, exceptions, GC and coroutine
  compatibility tests before widening language claims.
- Replace fake compiler output with an actual compiler adapter and actionable
  diagnostics. Add an incremental compiler daemon/cache with invalidation tests.
- Update code separately from native binaries; define state migration boundaries
  and safe runtime replacement with cancellation of obsolete work.
- If Kotlin output cannot execute correctly in the selected interpreter, publish
  that finding and choose a supported backend before building more SDK surface.

Exit: Kotlin logic changes alter counter behavior on the same running host,
without rebuilding the host, and work offline in a packaged release bundle.

### 3. Android and iOS vertical slice

- Reproducible host projects and generated UniFFI bindings, pinned app trust keys.
- Render the same compiled counter on a physical Android device and iPhone.
- Verify actions, state, refresh, navigation, keyboard, accessibility and errors.
- Test foreground/background, rotation, memory pressure, offline/reconnect and
  renderer/runtime lifecycle. Use main-thread rules and structured cancellation.

Exit: automated platform builds plus recorded device acceptance checks for the
same sample and protocol. iOS verification requires macOS/Xcode/device access.

### 4. App foundation and extension API

- Virtualized lists, keyed reconciliation, navigation/deep links, gestures,
  animations, text input, localization, accessibility and responsive layouts.
- HTTP/uploads/downloads, WebSockets, secure storage, SQLite, filesystem,
  permissions, notifications, background tasks, connectivity and auth adapters.
- Native view/module packaging, versioning, code generation and custom extensions.
- Define feature support per platform; unsupported features fail explicitly.

Exit: reference social feed and offline CRUD app, tested on both platforms;
third-party developers can add a new module without changing core or renderer.

### 5. Vertical capability packs

| Workload | Required framework capabilities | Acceptance application |
| --- | --- | --- |
| Music/podcasts | Streaming, background playback, lock-screen controls, audio focus, queues, offline downloads | Player survives app backgrounding and network loss |
| Movies/live video | Adaptive streaming, captions, DRM integration, casting, PiP, downloads | Licensed test stream plays with subtitles and recovers from interruptions |
| Chat/social (WhatsApp, Telegram, Discord, X, Instagram class) | Realtime sockets, durable storage, paging, media capture/upload, push, optimistic sync; native WebRTC and encryption adapters | Offline send queue, reconnect deduplication, media feed, call lifecycle |
| Mobility/maps (Uber class) | Native map views, routes, location permissions, background location, geofencing, payments | Location updates, permission denial, background route tracking |
| Commerce/productivity | Forms, validation, documents, camera/scanning, authentication, platform payment adapters | Accessible checkout/form and document workflow |

These are reference workloads, not claims to reproduce those companies' entire
products. Each pack needs native lifecycle tests, cancellation, resource and
permission-denial tests, and published Android/iOS capability differences.

### 6. Release engineering and production beta

- Signed immutable bundles, ABI/module version checks, no remote trust bootstrap.
- Atomic install, last-known-good recovery, rollback, staged deployment and
  update policy compatible with distribution channels.
- Reproducible release binaries, symbolication, crash reports with privacy
  controls, dependency auditing, secret handling and a documented threat model.
- Test performance on named low/mid/high devices: startup, steady-state memory,
  frame time/jank, long lists, upload/download, media and background battery use.
  Establish measured budgets before claiming React Native parity or superiority.
- Test accessibility, localization, poor networks, disk exhaustion, lifecycle,
  version migration and long-running resource leaks.

Exit: all required gates pass for declared supported capabilities, no unresolved
critical/high security findings, successful rollback drills, published limits,
and representative beta apps run by users outside the framework team.

## How work is tracked

Use acceptance gates rather than completion percentages. For each milestone,
record code, executable checks, platform evidence, unresolved defects and next
steps in docs/production-readiness.md. A source file or registered plugin alone
does not count as a working capability. CI passing on Linux does not establish
mobile correctness. Do not assign delivery dates until the compiler ABI and
first device slice have been proven.
