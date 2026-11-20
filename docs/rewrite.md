# Rewrite an existing screen into Feather

`feather-cli rewrite` reads a source screen and generates an editable Kotlin/JS
Feather project. It parses source files directly, so it needs no source app
build or Node dependency installation. Before handing over the generated app,
the CLI compiles it with Feather's Kotlin toolchain and renders it in the
Feather engine.

## React Native

```sh
feather-cli rewrite --source ./react-native-app --entry App.tsx --output ./feather-preview
feather-cli start --project ./feather-preview --phone
```

Choose a file with one outer JSX tree, or use `--component ScreenName` to
select a named component. A directory with one default
`App.tsx`, `App.jsx`, or `App.js` can omit `--entry`. The converter handles
views, text, scroll views, simple inline and file-scope `StyleSheet.create`
rules, controlled inputs, and buttons with supported local state handlers.
It translates literal string and integer `useState` values, string input
setters, and simple setter calls with a literal or an integer increment.
It can also expand a concise JSX `map` over a file-scope array of literal
objects and inline simple local components with destructured props. Complex
pressables retain their children while unsupported handlers are reported.
Other widgets, expressions, styles and handlers appear in
`migration-report.json`, with source lines where available.

## Android apps

For a Compose screen, choose a Kotlin file and the function to convert:

```sh
feather-cli rewrite --source ./android-app --entry app/src/main/java/Welcome.kt \
  --component Welcome --output ./feather-compose-preview
```

For Android layouts shared by Java and Kotlin projects, pass a layout XML
entry:

```sh
feather-cli rewrite --source ./android-app --entry app/src/main/res/layout/activity_main.xml \
  --output ./feather-xml-preview
```

Compose conversion covers common static layout calls and literal text.
XML conversion covers common containers, text, buttons, and local text input.
Android resource references, Compose state, and Kotlin/Java listeners require
manual migration.

## Iterate while Feather Go is connected

Open the private QR page from `start --phone`, then scan with Feather Go. If
the phone is on the same network, run the server with `--host 0.0.0.0`. A cloud
server needs a reachable forwarding URL, as described in the
[phone guide](phone-demo.md).

After editing the original source screen, run the same rewrite command again
with `--refresh`. A successful compile replaces the generated `App.kt` and
the running server's watcher delivers the change. Keep the server terminal
open for compiler errors and runtime logs. Refresh checks the original source
path and the generated file hash, so it refuses to overwrite hand-edited
Kotlin. Once you start editing the generated Kotlin directly, let the Feather
server watch those edits.

This first version migrates a selected screen. Navigation, persistence,
networking, animations, assets, and native dependencies still need explicit
migration and device testing.
