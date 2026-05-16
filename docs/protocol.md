# Feather Dev Protocol

The Phase 1 dev server sends JSON messages over `ws://<host>:<port>/ws`.

## Bundle Metadata

The dev server exposes the current bundle manifest at:

```text
GET http://<host>:<port>/bundle/manifest
```

The manifest points to the render artifact, SDK version, protocol version, asset list, and native module list.

Bundle artifacts are available at:

```text
GET http://<host>:<port>/bundle/artifact/<path>
```

## Render Message

```json
{
  "type": "render",
  "protocol_version": 1,
  "root": {
    "type": "Column",
    "children": [
      {
        "type": "Text",
        "text": "Hello, World!"
      }
    ]
  },
  "diagnostics": []
}
```

## Supported Widgets

- `Text`: renders a native text node.
- `StateText`: CLI-resolved state text; hosts receive it as `Text`.
- `Column`: renders child widgets vertically.
- `Row`: renders child widgets horizontally.
- `Button`: renders a native button and sends an `action` event to the CLI.
- `TextInput`: renders a native text field and sends an `action` event on value changes.

## Action Message

Hosts send action events back to the CLI when users interact with SDK widgets.

```json
{
  "type": "action",
  "action": "primaryButtonPressed",
  "payload": null
}
```

Text input events use the same shape, with `payload` set to the current text value.

## State Resolution

The CLI owns Phase 1 runtime state. When it receives an `action` event, it updates `state[action]`.

- String payloads replace the action state value.
- Null payloads increment the action state value as a counter.
- `StateText` nodes are resolved by the CLI before payloads reach the host.

## Current Prototype Limitation

The CLI now prefers generated Feather IR from KSP when `generatedIr` is present in `feather.json`. If that file is missing, it falls back to a checked-in IR file or the temporary source extractor.
