# Feather Bundler v0

Feather Bundler v0 creates a local `.featherbundle` directory for the active project.

## Bundle Layout

```text
.featherbundle/
  manifest.json
  app.feather.json
  modules.json
  assets/
```

## Manifest

`manifest.json` contains:

- `name`: project name.
- `bundle_version`: bundler format version.
- `sdk_version`: Feather SDK version expected by the bundle.
- `protocol_version`: WebSocket render protocol version.
- `render`: render artifact path, SHA-256, and byte length.
- `assets`: static asset artifacts.
- `modules`: native module requirements.
- `dependencies`: source and generated files that influenced the bundle.
- `delta`: changed and removed artifact paths compared with the previous manifest.

## Current Behavior

- The CLI prefers generated KSP IR.
- If generated IR is missing, the CLI uses checked-in fallback IR.
- If both are missing, the CLI uses the temporary source extractor.
- `StateText` is resolved while writing `app.feather.json`.
- `GET /bundle/manifest` serves current bundle metadata.
- `GET /bundle/artifact/app.feather.json` serves a bundle artifact.
- `assets` entries in `feather.json` are copied into `.featherbundle/assets`.
- Native module requirements (including platform support and required permissions) are discovered from IR nodes with `module` fields.
- Delta metadata is computed from the previous `.featherbundle/manifest.json`.
- Bundle manifest, delta, artifact checksum, module discovery, and state resolution primitives live in `core`.
- Android and iOS hosts can fetch the manifest/artifact path before subscribing to WebSocket updates.

## Next Work

1. Add binary-safe artifact responses with content types.
2. Add host-side bundle cache and delta download.
3. Add real native module declarations from SDK/compiler metadata.
4. Add signing for production bundles.
5. Replace the KSP source scanner with symbol-aware IR generation.
