# Feather General Platform Roadmap

This roadmap turns Feather from the current prototype into a general app platform like Expo Go for Kotlin-first native apps. The goal is not only to support media apps like YouTube, Netflix, and Spotify, but also ecommerce, mobility, finance, social, productivity, games, and custom business apps.

## Guiding Architecture

Feather apps should import Feather APIs, not Android or iOS implementation details.

Primary planning artifacts:

- `docs/platform-contract.md`
- `docs/current-gaps.md`

The platform boundary should be:

1. App code uses `dev.feather.sdk.*`.
2. Compiler/CLI turns app code into a stable Feather render/runtime artifact.
3. Core owns state, actions, module calls, diagnostics, and bundle metadata.
4. Host apps render native UI and provide native module implementations.
5. Official modules ship with Feather hosts; third-party modules plug into the same registry.

This keeps apps portable. Android uses Compose and Android services internally. iOS uses SwiftUI and iOS frameworks internally. App code should not care.

## Phase 0: Stabilize The Current Prototype

Goal: make the existing dev loop reliable before expanding features.

- [ ] Verify Android host compiles after recent media/WebView work.
- [ ] Fix any remaining `MainActivity.kt` syntax, import, or Compose API issues.
- [ ] Remove stale comments and placeholder branches that no longer match behavior.
- [ ] Confirm `feather start --project samples/hello-world` still streams a render payload.
- [ ] Confirm Android host can fetch `/bundle/manifest` and `/bundle/artifact/app.feather.json`.
- [ ] Update `STATUS.md` after each completed checkpoint.

Exit criteria:

- `cargo check` passes for `cli`.
- Android host compiles on a machine or CI with enough RAM.
- Hello-world renders through the host without manual app-specific Android changes.

## Phase 1: Define The Platform Contract [DONE]

Goal: make one source of truth for what Feather apps can use.

- [x] Create a formal `docs/platform-contract.md`.
- [x] Define core widget categories:
  - layout
  - text/input
  - images/assets
  - lists/grids
  - navigation
  - overlays
  - gestures
  - media
  - web
  - device/system
- [x] Define official module categories:
  - `feather-network`
  - `feather-storage`
  - `feather-navigation`
  - `feather-media`
  - `feather-webview`
  - `feather-image`
  - `feather-device`
  - `feather-files`
  - `feather-auth`
  - `feather-notifications`
  - `feather-location`
  - `feather-camera`
  - `feather-payments`
  - `feather-maps`
- [x] Define module call shape as structured data instead of string commands.
- [x] Define action event shape.
- [x] Define async callback/result shape.
- [x] Define error shape for module failures.
- [x] Define permission declaration shape.
- [x] Define bundle manifest module requirements.
- [x] Define version compatibility rules between app SDK, CLI, core, host, and modules.

Exit criteria:

- SDK, CLI, core, and host can all point to the same protocol document.
- No new module should be added without matching contract text.

## Phase 2: Make Core The Runtime Source Of Truth

Goal: stop duplicating runtime logic in each host.

- [ ] Audit `core/src/lib.rs` exported APIs.
- [ ] Keep `AppRuntime` as the owner of:
  - state
  - action dispatch
  - module call queue
  - async module results
  - diagnostics
  - render-needed polling
- [ ] Decide whether render JSON parsing stays host-side or moves into typed core structures.
- [ ] Generate/update UniFFI bindings for Android.
- [ ] Generate/update UniFFI bindings for iOS.
- [ ] Make Android host instantiate `AppRuntime`.
- [ ] Make Android host send actions into `AppRuntime.apply_action_event`.
- [ ] Make Android host poll/drain module calls through core rather than parsing `host:` strings directly.
- [ ] Make host diagnostics use core console/diagnostic buffers.
- [ ] Remove duplicated state assignment logic from Android once core owns it.

Exit criteria:

- Android host state behavior and CLI state behavior are consistent because both pass through core.
- Module calls can be queued and resolved by core.

## Phase 3: Build The Native Module Registry

Goal: support Expo-Go-style bundled modules and future third-party modules.

- [x] Add Android `FeatherModule` interface.
- [x] Add Android `ModuleRegistry`.
- [ ] Add iOS `FeatherModule` protocol if not already present.
- [ ] Add iOS `ModuleRegistry`.
- [x] Register first official modules at host startup.
- [ ] Add module capability lookup by name and version.
- [ ] Add missing-module diagnostics when a bundle requires an unavailable module.
- [ ] Add unsupported-platform diagnostics when a module exists on one platform only.
- [ ] Add permission metadata for modules that require native permissions.
- [ ] Add module lifecycle hooks:
  - initialize
  - start
  - stop
  - destroy
  - handle permission result
  - handle activity/result callback
- [ ] Move direct Android `host:` handling behind module implementations.

Exit criteria:

- The host can render built-in widgets directly and delegate module nodes/actions through a registry.
- Third-party modules have the same shape as official modules.

## Phase 4: Core Official Modules

Goal: ship enough native capability for real general apps.

### `feather-network`

- [ ] Support `GET`, `POST`, `PUT`, `PATCH`, `DELETE`.
- [ ] Support headers.
- [ ] Support JSON bodies.
- [ ] Support form bodies.
- [ ] Support file upload.
- [ ] Support download progress.
- [ ] Support cancellation.
- [ ] Return status code, headers, body, and error separately.

### `feather-storage`

- [ ] Key-value storage.
- [ ] Secure storage.
- [ ] JSON object storage.
- [ ] Namespaced app storage.
- [ ] Clear/remove operations.
- [ ] Migration/version support.

### `feather-navigation`

- [ ] Navigate.
- [ ] Go back.
- [ ] Replace.
- [ ] Reset.
- [ ] Route params.
- [ ] Deep link entry.
- [ ] Back-stack state.
- [ ] Android TV remote back handling.

### `feather-media`

- [ ] Video player.
- [ ] Audio player.
- [ ] Playback controls.
- [ ] Fullscreen video.
- [ ] Background audio policy.
- [ ] Subtitles/captions.
- [ ] Track selection.
- [ ] Picture-in-picture where supported.
- [ ] Media session integration.

### `feather-webview`

- [ ] Render URL.
- [ ] Render HTML string.
- [ ] Navigation callbacks.
- [ ] JavaScript toggle.
- [ ] DOM storage toggle.
- [ ] Message bridge.
- [ ] Download/file chooser handling.

### `feather-image`

- [ ] Remote image loading.
- [ ] Local asset loading.
- [ ] Placeholder/error states.
- [ ] Disk/memory cache policy.
- [ ] SVG support decision.
- [ ] Animated image support decision.

### `feather-device`

- [ ] Haptics.
- [ ] Clipboard.
- [ ] Share sheet.
- [ ] App info.
- [ ] Device info.
- [ ] Screen dimensions.
- [ ] Orientation.
- [ ] Safe areas.
- [ ] Battery/network status.

### `feather-files`

- [ ] Read app files.
- [ ] Write app files.
- [ ] Delete app files.
- [ ] List directories.
- [ ] Download files.
- [ ] Pick files.
- [ ] Share files.
- [ ] Cache directory support.

### `feather-auth`

- [ ] Biometric prompt.
- [ ] OAuth flow.
- [ ] Token callback.
- [ ] Secure token storage.
- [ ] Logout/revoke helpers.

### `feather-notifications`

- [ ] Permission request.
- [ ] Local notification scheduling.
- [ ] Cancel notifications.
- [ ] Push token retrieval.
- [ ] Notification tap event.
- [ ] Foreground notification behavior.

### `feather-location`

- [ ] Permission request.
- [ ] Current location.
- [ ] Watch position.
- [ ] Stop watch.
- [ ] Accuracy options.
- [ ] Background location policy.

### `feather-camera`

- [ ] Take photo.
- [ ] Pick image.
- [ ] Record video.
- [ ] Camera preview widget.
- [ ] Barcode/QR scanning.
- [ ] Permission flow.

### `feather-maps`

- [ ] Map view.
- [ ] Markers.
- [ ] Current location.
- [ ] Camera movement.
- [ ] Polylines.
- [ ] Tap callbacks.

### `feather-payments`

- [ ] Payment-sheet abstraction.
- [ ] Platform payment integration decision.
- [ ] Stripe/provider plugin path.
- [ ] Result callbacks.
- [ ] Secure error handling.

Exit criteria:

- A broad app can be built without app-specific native Android/iOS code for common capabilities.

## Phase 5: SDK Cleanup And Generalization

Goal: make `sdk` pleasant, typed, and honest about supported features.

- [ ] Split `Feather.kt` into focused files by area.
- [ ] Replace string-built module commands with typed request objects.
- [ ] Add `@Serializable` request/response models for each module.
- [ ] Add stable public APIs for actions and callbacks.
- [ ] Add route params and typed navigation APIs.
- [ ] Add lifecycle APIs for app startup/resume/pause.
- [ ] Add platform capability checks.
- [ ] Mark unsupported/incomplete APIs as experimental.
- [ ] Align SDK widget classes with host-rendered nodes.
- [ ] Remove SDK APIs that have no host/core implementation or explicitly mark them pending.

Exit criteria:

- SDK docs match actual host behavior.
- Calling a supported SDK API has a defined runtime path.

## Phase 6: Compiler And Parser Upgrade

Goal: move away from fragile string scanning.

- [ ] Document current fallback parser limits.
- [ ] Expand KSP to use resolver/symbol APIs instead of raw source scanning.
- [ ] Support nested layouts through real symbols.
- [ ] Support variables and constants.
- [ ] Support function components.
- [ ] Support conditionals.
- [ ] Support loops/list rendering.
- [ ] Support imports across files.
- [ ] Support custom module widgets.
- [ ] Emit stable node IDs.
- [ ] Emit diagnostics with file, line, and column.
- [ ] Keep fallback parser only as a development escape hatch.
- [ ] Decide long-term path:
  - KSP IR generation
  - Kotlin/WASM runtime
  - hybrid KSP for UI and WASM for logic

Exit criteria:

- Real Kotlin app code compiles to Feather artifacts without relying on brittle text parsing.

## Phase 7: CLI Productionization

Goal: make `feather` behave like a real platform tool.

- [ ] Harden `feather start`.
- [ ] Make file watching ignore build/cache noise.
- [ ] Add structured device logs.
- [ ] Add structured host errors.
- [ ] Add bundle validation.
- [ ] Add `feather doctor` checks for:
  - SDK version
  - CLI version
  - host compatibility
  - module compatibility
  - missing permissions
  - invalid manifest
  - missing generated IR/WASM
- [ ] Make `feather install` resolve real module metadata.
- [ ] Make `feather install` update manifest, lockfile, permissions, and platform dependency metadata.
- [ ] Add `feather uninstall`.
- [ ] Add `feather update`.
- [ ] Add `feather build android`.
- [ ] Add `feather build ios`.
- [ ] Add `feather prebuild` if app-specific native shells become necessary.
- [ ] Add bundle signing/checksums for production.

Exit criteria:

- CLI can initialize, run, inspect, install modules, and produce production artifacts predictably.

## Phase 8: Bundle And Asset Pipeline

Goal: make apps portable and production-ready.

- [ ] Finalize bundle manifest schema.
- [ ] Include render artifact.
- [ ] Include WASM artifact when present.
- [ ] Include assets.
- [ ] Include fonts.
- [ ] Include module requirements.
- [ ] Include permissions.
- [ ] Include app metadata.
- [ ] Include deep link config.
- [ ] Include platform-specific overrides.
- [ ] Add asset hashing.
- [ ] Add delta updates.
- [ ] Add remote bundle download path.
- [ ] Add local bundle loading path.
- [ ] Add rollback behavior for failed updates.

Exit criteria:

- A production host can load a signed Feather bundle and know exactly which modules/assets it needs.

## Phase 9: Android Host Maturity

Goal: Android host becomes the reference implementation.

- [ ] Split `MainActivity.kt` into:
  - activity/bootstrap
  - renderer
  - node parsing
  - style mapping
  - module registry
  - official modules
  - diagnostics overlay
- [ ] Add real TV focus support.
- [ ] Add keyboard/controller input handling.
- [ ] Add back handling through navigation module.
- [ ] Add permission request routing.
- [ ] Add activity result routing.
- [ ] Add lifecycle routing.
- [ ] Add module registry.
- [ ] Add official module implementations.
- [ ] Add local bundle mode.
- [ ] Add dev-server mode.
- [ ] Add production bundle mode.
- [ ] Add logging bridge to CLI.
- [ ] Add crash/error diagnostics overlay.

Exit criteria:

- Android supports general apps without hand-editing host source for each app.

## Phase 10: iOS Host Parity

Goal: iOS should follow the same contract, not a separate product.

- [ ] Create/repair iOS host if missing.
- [ ] Wire core via UniFFI.
- [ ] Implement renderer parity for core widgets.
- [ ] Implement module registry.
- [ ] Implement official module set.
- [ ] Implement dev-server WebSocket connection.
- [ ] Implement local bundle loading.
- [ ] Implement production bundle loading.
- [ ] Implement diagnostics overlay.
- [ ] Document platform differences honestly.

Exit criteria:

- A Feather app that avoids platform-specific modules runs on Android and iOS from the same app code.

## Phase 11: General App Readiness Tracks

Goal: validate Feather against real app categories.

### Media App Track

- [ ] YouTube-like video browsing.
- [ ] Netflix-like catalog and playback.
- [ ] Spotify-like audio playback.
- [ ] Background media behavior.
- [ ] TV remote support.

### Ecommerce Track

- [ ] Product grid/list.
- [ ] Product detail.
- [ ] Cart.
- [ ] Checkout.
- [ ] Auth.
- [ ] Payments.
- [ ] Push notifications.

### Mobility Track

- [ ] Map view.
- [ ] Location watch.
- [ ] Ride/order state.
- [ ] Realtime polling/WebSocket.
- [ ] Push notifications.
- [ ] Payments.

### Social/Productivity Track

- [ ] Feed/list performance.
- [ ] Compose/post screen.
- [ ] File/image picker.
- [ ] Uploads.
- [ ] Offline cache.
- [ ] Share sheet.

Exit criteria:

- Each category has a sample that uses only Feather APIs and official modules.

## Phase 12: Testing Strategy

Goal: avoid breaking the contract as the platform grows.

- [ ] Add core Rust unit tests for state/actions/modules.
- [ ] Add CLI tests for manifest, bundling, module discovery, and doctor.
- [ ] Add SDK serialization tests.
- [ ] Add KSP fixture tests.
- [ ] Add Android host compile tests.
- [ ] Add Android renderer screenshot tests where feasible.
- [ ] Add module contract tests.
- [ ] Add end-to-end sample smoke tests.
- [ ] Add CI matrix for:
  - Rust core/CLI
  - Kotlin SDK/compiler
  - Android host
  - sample bundle generation

Exit criteria:

- Changes to SDK, CLI, core, or host fail fast when they break the shared contract.

## Phase 13: Documentation And Developer Experience

Goal: make Feather usable by someone who did not build it.

- [ ] Rewrite README around the actual platform model.
- [ ] Add quickstart.
- [ ] Add module docs.
- [ ] Add SDK reference.
- [ ] Add app architecture guide.
- [ ] Add production build guide.
- [ ] Add troubleshooting guide.
- [ ] Add Android host guide.
- [ ] Add iOS host guide.
- [ ] Add module authoring guide.
- [ ] Add compatibility/versioning guide.

Exit criteria:

- A new developer can create and run a Feather app without reading source code.

## Phase 14: Release Channels

Goal: make Feather shippable.

- [ ] Define versioning policy.
- [ ] Define SDK/CLI/host compatibility matrix.
- [ ] Add changelog.
- [ ] Add release artifacts for CLI.
- [ ] Add Android host release builds.
- [ ] Add iOS host release builds.
- [ ] Add module registry metadata.
- [ ] Add rollback policy.
- [ ] Add security review for module permissions and remote bundles.

Exit criteria:

- Feather can ship preview releases without breaking existing apps silently.

## Immediate Next Steps

Start here before adding more app-specific features:

1. Add missing-module diagnostics in the Android host.
2. Update SDK commands from raw strings toward typed module requests.
3. Update CLI `doctor` to report unsupported SDK APIs and missing host modules.
4. Keep `youtube-native` as a validation app, not as the architecture driver.
