const vscode = require("vscode");

function activate(context) {
    context.subscriptions.push(vscode.commands.registerCommand("feather.doctor", async () => {
        if (!vscode.workspace.isTrusted) {
            await vscode.window.showWarningMessage("Feather Doctor requires a trusted workspace.");
            return;
        }
        const folders = vscode.workspace.workspaceFolders ?? [];
        if (folders.length === 0) {
            await vscode.window.showInformationMessage("Open a Feather project folder to run Doctor.");
            return;
        }
        const editor = vscode.window.activeTextEditor;
        const folder = (editor && vscode.workspace.getWorkspaceFolder(editor.document.uri))
            ?? (folders.length === 1 ? folders[0] : await vscode.window.showWorkspaceFolderPick());
        if (!folder) return;

        const file = await vscode.window.showOpenDialog({
            title: "Select the Feather project manifest",
            canSelectMany: false,
            defaultUri: folder.uri,
            filters: { "JSON files": ["json"] }
        });
        if (!file) return;
        if (file[0].path.split("/").pop() !== "feather.json") {
            await vscode.window.showErrorMessage("Select a feather.json project manifest.");
            return;
        }

        const project = vscode.Uri.joinPath(file[0], "..").fsPath;
        const cli = vscode.workspace.getConfiguration("feather").get("cliPath") || "feather-cli";
        const execution = new vscode.ProcessExecution(cli, ["doctor", "--project", project], { cwd: project });
        const task = new vscode.Task({ type: "feather" }, folder, "Doctor", "Feather", execution, []);
        await vscode.tasks.executeTask(task);
    }));
}

module.exports = { activate };
