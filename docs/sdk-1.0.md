# Feather SDK 1.0

Feather SDK 1.0 establishes the first stable prototype contract between KMP app code, the Rust CLI, and native host apps.

## Included Surface

- `Widget`: sealed root type for all renderable nodes.
- `Text`: static text.
- `StateText`: text derived from CLI runtime state.
- `Button`: native button with an action identifier.
- `TextInput`: native text field with change action events.
- `Column` and `Row`: basic layout containers.
- `State`: declarative state key placeholder for the upcoming runtime.
- `FeatherModule`: base contract for future native modules.
- `FeatherApp`: app entry point with `render()` and `handleAction()`.

## Runtime Contract

The CLI compiles the app entry into Feather protocol JSON. The Android host renders that JSON using native Compose widgets. Interactive widgets send action events back to the CLI. The CLI updates runtime state from those events and sends a new render payload.

## Compiler Path

SDK 1.0 uses `:compiler:ksp` as the first generated IR path. The KSP processor finds the `FeatherApp.render()` source, emits `app.feather.json`, and the CLI prefers that generated file when `feather.json` defines `generatedIr`.

If the generated file does not exist yet, the sample uses `.feather/app.feather.json` as a checked-in fallback so low-memory development can continue without running Gradle.

## Next Required Runtime Work

1. Replace KSP source scanning with symbol/IR-aware rendering for nested layouts and expressions.
2. Add stable node IDs and tree diffing so hosts receive patches instead of full renders.
3. Route action events into `FeatherApp.handleAction()` instead of handling them only in the CLI.
4. Persist runtime state snapshots keyed by `State.key`.
5. Add production signing, release channels, and rollback policy.
