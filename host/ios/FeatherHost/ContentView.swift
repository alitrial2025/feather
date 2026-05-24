import SwiftUI

struct ContentView: View {
    @StateObject private var client = FeatherDevClient()
    @Environment(\.scenePhase) private var scenePhase
    @State private var showLauncher = true

    var body: some View {
        ZStack {
            if showLauncher {
                LauncherScreen { newHost in
                    client.updateURLs(hostOrURL: newHost)
                    showLauncher = false
                    client.resetAndConnect()
                    client.start()
                }
            } else {
                VStack(spacing: 0) {
                    // Connection status indicator
                    HStack {
                        Circle()
                            .fill(client.isConnected ? Color.green : Color.red)
                            .frame(width: 8, height: 8)
                        Text(client.isConnected ? "Connected" : "Disconnected")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Button(action: {
                            showLauncher = true
                        }) {
                            Image(systemName: "gearshape.fill")
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 4)
                    .background(Color(.systemGray6))

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
}
