# Feather Device Info

Run `feather-cli start --phone --project samples/device-info` from the
repository root. Open the project in Feather Go and tap **Read device info**.
The app displays the platform, OS version, and model returned by the host.
The sample uses the built-in `feather-device` module and does not need
vendor credentials or hardware permissions.

`Platform.getDeviceInfo("device_info")` produces an SDK action descriptor.
The host routes the action to its native module, then writes the returned JSON
to the requested state key. The app uses `getStateJson<DeviceDetails>` to
read that state on the next render. This is a starting point for adding
typed Kotlin wrappers around your own host module methods.

In a standalone build, opening `feather://app/device-info` displays the
opened path. The signed bundle declares `/device-info` and its child paths.
The host delivers an accepted path as the payload of `__deepLink`, and the
sample handles it in `FeatherApp.handleAction`. Pairing uses
`feather://open`. App links require an embedded signed bundle.
