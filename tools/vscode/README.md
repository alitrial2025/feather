# Feather UI for VS Code

Adds syntax highlighting, bracket pairing, comments, and a starter snippet for
Feather UI (`.fthr`). Open the Command Palette and select **Feather: Run Doctor**
to check a project in the integrated terminal. Choose the project's
`feather.json` when prompted. Doctor requires a trusted workspace and
`feather-cli` on PATH. Set `feather.cliPath` in user settings to use an
absolute executable path.

To package the extension locally, run `npx @vscode/vsce package` from this
directory and install the resulting VSIX using **Extensions: Install from
VSIX**.
