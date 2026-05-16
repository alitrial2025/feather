import SwiftUI

struct ContentView: View {
    @StateObject private var client = FeatherDevClient()

    var body: some View {
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
                Text("Feather Dev")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal)
            .padding(.vertical, 4)
            .background(Color(.systemGray6))

            // Main content
            ZStack {
                ScrollView {
                    DynamicView(node: client.root, onAction: client.sendAction)
                        .padding()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                if !client.errors.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Feather Diagnostics")
                            .font(.headline)
                            .foregroundColor(.white)
                            .padding(.bottom, 4)
                        
                        ForEach(client.errors, id: \.self) { error in
                            Text(error)
                                .font(.system(size: 14, weight: .medium, design: .monospaced))
                                .foregroundColor(.white)
                        }
                        Spacer()
                    }
                    .padding()
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .background(Color.red.opacity(0.95))
                    .transition(.opacity)
                }
            }
        }
        .onAppear {
            client.start()
        }
    }
}
