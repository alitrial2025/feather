# Feather Go and the first chat demo

Feather Go is the persistent Android development client. Install its APK once,
scan the server's project QR, and leave the app open while editing
`samples/whatsapp/App.kt`. The app uses Feather SDK widgets and action handlers;
it is compiled by the Kotlin compiler and runs inside Feather's QuickJS/Rust
engine. Android's Compose renderer belongs to the reusable host. There is no
separate native WhatsApp application to rebuild on each edit.

## Start the session

Use the native build prerequisites in [Windows development](windows-development.md).
On Windows, run the build/server commands inside WSL2. An existing compatible
Feather Go APK can be installed directly without building the Android host again.

```sh
cargo build --locked --manifest-path cli/Cargo.toml
python3 scripts/build_android_sample.py --project samples/whatsapp --dev-host
python3 scripts/serve_phone_demo.py
```

The APK is `build/native/android/feather-dev-host-debug.apk` (ARM64 Android 7+).
The server prints a connection-page URL and a project QR. Open the connection
page on the computer, download/install Feather Go on the phone, and scan the QR
inside Feather Go. The page also offers **Open this project** for opening its
link on the same phone after installation. Permit camera access when scanning.
This is a debug client, not a store release.

A remote cloud workspace needs an externally reachable HTTP/WebSocket URL.
After configuring that forwarding, restart with
`python3 scripts/serve_phone_demo.py --public-url https://your-forwarded-host`.
This command itself does not create a third-party tunnel. For local development,
use the computer's reachable LAN address; localhost on a phone is the phone
itself. WSL networking/firewall settings may require Windows port forwarding.

All HTTP and WebSocket project routes are under a random `/session/<token>`
prefix. The server serves only the explicitly selected client APK and bundle
artifacts, rather than the workspace filesystem. Keep the connection URL/QR
private: possession allows downloading development artifacts and accessing the
session's DevTools. Delete `.feather/phone-session.json` and restart to rotate it.

The QR includes the project signing public key. The debug client pairs using
that key and verifies the manifest and code before executing it. A release host
rejects QR key replacement and uses its pinned build key. Private signing keys
remain on the development machine. Compiled code and the APK are transmitted to
phones (and through any configured forwarding service).

## Edit without rebuilding the APK

Change the Kotlin app, save, and wait for the successful compiler update. The
client downloads signed code and retains shared runtime state. Compiler errors
keep the last good application. Kotlin compilation currently takes seconds;
this is not yet React Native/Expo-level refresh speed. Changes to native host
code or to the set of compiled-in native modules still need a new APK.

The demo currently supports seeded conversations, filters, search, locally
sending messages, new-chat selection, group listing, and posting local status.
Messages survive compatible code refresh, but do not yet survive a process
restart. Contacts are fixtures; this does not connect to WhatsApp. Accounts,
server persistence, real multi-user messaging, push delivery, attachments,
encryption, and voice/video calling remain to be implemented. Calls are
explicitly marked as coming next.

## Validation and next milestones

`python3 scripts/smoke_phone_demo.py` checks the scoped connection page, APK
route, QR identity, signed artifacts, and native Kotlin/JNA execution of chat
filters, search, sending, status updates and live source refresh with independent
client state retained. It requires the shared build cache/toolchain populated by
`scripts/smoke_native_bindings.py`. Android unit tests cover HTTPS paths, ports,
keys and rejected release pairing. APK packaging checks every native library's
16 KB ELF/ZIP alignment. Physical Android UI/camera execution still needs a
phone; native JVM execution does not substitute for that check.

1. Connect the real Android phone and verify scan, render, typing, keyboard,
   message send, edit refresh, disconnect/reconnect and camera permission.
2. Add persistent local storage and a real messaging backend with test users,
   conversations, authenticated WebSockets, delivery states and pagination.
3. Add attachments, notifications, offline synchronization and error recovery.
4. Add calls using a validated native media/WebRTC integration, with new host
   builds only when native capabilities change.
5. Profile, test device/API coverage and complete release signing and security
   validation before claiming production readiness.
