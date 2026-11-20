# Signed release channels

Build and promote a Kotlin/JS runtime bundle. Use one registry directory per
app, with channel names that identify the target and environment.

```sh
feather-cli build --project ./my-app --target android
feather-cli release --store ./my-app/dist/releases --channel android-preview \
  --key ./my-app/.feather/public_key.txt promote "./my-app/dist/android/bundles/<bundle-id>"
```

Replace the last argument with the bundle directory printed by `build`.
Supply a publisher public key you trust independently of the incoming bundle.
For a team or CI job, configure that file from the release trust configuration.
Keep the private signing key out of the registry and published artifacts.

Promotion snapshots the verified manifest and its declared runtime artifacts
in `bundles/<bundle-id>/`. The ID is the SHA-256 of the manifest bytes.
`channels/android-preview.json` contains the app identity, active bundle ID,
and previous bundle ID. Re-promoting the active bundle preserves the rollback
target. A verification failure leaves the active pointer intact.

```sh
feather-cli release --store ./my-app/dist/releases --channel android-preview \
  --key ./my-app/.feather/public_key.txt rollback
```

Rollback verifies the previous bundle before swapping the pointers. The CLI
accepts Kotlin/JS releases from `feather-cli build` with the supported SDK and
protocol versions. The registry is a local staging format. Hosted deployment,
access control, device-side channel polling, and rollout policies still need
integration.
