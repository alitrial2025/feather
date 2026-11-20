import SwiftUI

struct ContentView: View {
    @StateObject private var client = FeatherDevClient()
    @Environment(\.scenePhase) private var scenePhase
    @State private var showLauncher = true
    @State private var invalidProjectLink = false
    @State private var showProjectTools = false

    var body: some View {
        ZStack {
            if showLauncher {
                LauncherScreen(onConnect: connect)
            } else {
                VStack(spacing: 0) {
                    // Connection status indicator
                    if client.canManageProject { HStack {
                        Circle()
                            .fill(client.isConnected ? Color.green : Color.red)
                            .frame(width: 8, height: 8)
                        Text(client.isConnected ? "Connected" : "Disconnected")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Button(action: {
                            showProjectTools = true
                        }) {
                            Image(systemName: "gearshape.fill")
                                .foregroundColor(.secondary)
                                .accessibilityLabel("Feather Go project tools")
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 4)
                    .background(Color(.systemGray6))
                    }

                    // Main content
                    ZStack {
                        ScrollView {
                            DynamicView(node: client.root, moduleRegistry: client.moduleRegistry, onAction: client.sendAction)
                                .padding()
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                        DiagnosticsOverlay(errors: client.errors) {
                            client.resetAndConnect()
                        }
                    }
                }
            }
        }
        .onAppear {
            if showLauncher && Bundle.main.url(forResource: "app", withExtension: "js", subdirectory: "feather") != nil {
                showLauncher = false
                client.start()
            }
        }
        .onOpenURL { url in
            if url.scheme == "feather", url.host == "app" {
                if showLauncher && Bundle.main.url(forResource: "app", withExtension: "js", subdirectory: "feather") != nil {
                    showLauncher = false
                    client.start()
                }
                client.openAppLink(url)
                return
            }
            #if DEBUG
            if url.scheme == "feather" { connect(url.absoluteString) }
            #endif
        }
        .alert("Invalid Feather project link", isPresented: $invalidProjectLink) {
            Button("OK", role: .cancel) { }
        }
        .sheet(isPresented: $showProjectTools) {
            NavigationStack {
                List {
                    Section("Project") {
                        Text(client.isConnected ? "Connected" : "Disconnected")
                        Text(client.projectEngine)
                        Button(client.isConnected ? "Check for update" : "Reconnect") {
                            client.refreshProject()
                            showProjectTools = false
                        }
                        Button("Close project", role: .destructive) {
                            client.closeProject()
                            showProjectTools = false
                            showLauncher = true
                        }
                    }
                    Section("Native modules") {
                        ForEach(client.projectModules, id: \.name) { module in
                            LabeledContent(module.name, value: module.version)
                        }
                    }
                }
                .navigationTitle("Project tools")
            }
        }
        .onChange(of: scenePhase) { newPhase in
            switch newPhase {
            case .active:
                client.moduleRegistry.onStart()
            case .inactive, .background:
                client.moduleRegistry.onStop()
            @unknown default:
                break
            }
        }
    }

    private func connect(_ address: String) {
        guard Bundle.main.url(forResource: "app", withExtension: "js", subdirectory: "feather") == nil else {
            showLauncher = false
            return
        }
        guard client.updateURLs(hostOrURL: address) else {
            invalidProjectLink = true
            return
        }
        showLauncher = false
        client.resetAndConnect()
    }
}
