import Foundation
import UIKit
// Note: `import FeatherCore` would be used here when the UniFFI-generated
// Swift bindings and the compiled `libfeather_core.a` are linked into the Xcode project.
// For now, we add stub declarations that match the UniFFI-generated interface.

// MARK: - UniFFI Stub (replaced by generated bindings at build time)

#if canImport(FeatherCore)
import FeatherCore
#else
/// Stub matching the UniFFI-generated Swift interface.
/// Replaced at link time by the real Rust library.
class AppRuntime {
    init() {}
    func loadWasm(wasmBytes: [UInt8]) throws {}
    func hotReloadWasm(wasmBytes: [UInt8]) throws {}
    func hasWasm() -> Bool { return false }
    func renderWasm() -> String? { return nil }
    func pollRenderNeeded() -> Bool { return false }
    func applyActionEvent(payload: String) {}
    func drainConsoleLogs() -> [String] { return [] }
    func deliverModuleResult(callbackId: String, resultJson: String) {}
    func deliverModuleError(callbackId: String, error: String) {}
}
#endif

@MainActor
final class FeatherDevClient: NSObject, ObservableObject {
    @Published var root: FeatherNode = .text(id: UUID().uuidString, text: "Waiting for Feather CLI...")
    @Published var isConnected: Bool = false
    @Published var lastDelta: BundleDelta? = nil
    @Published var errors: [String] = []

    // ─── WASM Runtime ────────────────────────────────────────────────────────
    private var wasmRuntime: AppRuntime?
    private var wasmRenderTimer: Timer?

    private let baseURL: URL
    private let socketURL: URL
    private var socket: URLSessionWebSocketTask?
    private var reconnectTimer: Timer?
    private let maxReconnectAttempts = 5
    private var reconnectAttempts = 0

    init(host: String = "127.0.0.1", port: Int = 8080) {
        self.baseURL = URL(string: "http://\(host):\(port)")!
        self.socketURL = URL(string: "ws://\(host):\(port)/ws")!
        super.init()
    }

    // MARK: - App Initialization
    
    func start() {
        // ─── Priority 1: WASM Runtime ─────────────────────────────────────────
        if let wasmUrl = Bundle.main.url(forResource: "app", withExtension: "wasm", subdirectory: "feather"),
           let wasmData = try? Data(contentsOf: wasmUrl) {
            do {
                let runtime = AppRuntime()
                try runtime.loadWasm(wasmBytes: [UInt8](wasmData))
                wasmRuntime = runtime
                isConnected = true

                // Initial render
                if let json = runtime.renderWasm(),
                   let data = json.data(using: .utf8),
                   let (node, newErrors) = try? FeatherRenderPayload.decode(data) {
                    root = node
                    errors = newErrors
                }

                // Start the render polling loop (~60fps)
                startWasmRenderLoop()
                return
            } catch {
                errors = ["WASM load failed: \(error.localizedDescription)"]
            }
        }

        // ─── Priority 2: AOT JSON Bundle ──────────────────────────────────────
        if let localUrl = Bundle.main.url(forResource: "app.feather", withExtension: "json"),
           let data = try? Data(contentsOf: localUrl) {
            do {
                let (node, newErrors) = try FeatherRenderPayload.decode(data)
                root = node
                errors = newErrors
                isConnected = true
                return
            } catch {
                errors = ["Failed to load local AOT bundle: \(error.localizedDescription)"]
            }
        }
        
        // ─── Priority 3: Dev Server ───────────────────────────────────────────
        connectWebSocket()
        Task {
            await fetchBundleRender()
        }
    }

    // MARK: - WASM Render Loop

    private func startWasmRenderLoop() {
        wasmRenderTimer?.invalidate()
        wasmRenderTimer = Timer.scheduledTimer(withTimeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, let runtime = self.wasmRuntime else { return }
                
                // Phase 17: Drain console logs for error boundary overlay
                let logs = runtime.drainConsoleLogs()
                let errorLogs = logs.filter { $0.localizedCaseInsensitiveContains("error") || $0.localizedCaseInsensitiveContains("exception") }
                if !errorLogs.isEmpty {
                    self.errors = errorLogs
                }
                
                if runtime.pollRenderNeeded() {
                    if let json = runtime.renderWasm(),
                       let data = json.data(using: .utf8),
                       let (node, newErrors) = try? FeatherRenderPayload.decode(data) {
                        self.root = node
                        self.errors = newErrors
                    }
                }
            }
        }
        
        // Phase 17: Connect to dev server for hot-reload even when running WASM
        connectWasmHotReload()
    }

    // MARK: - Hot Reload (Phase 17)

    private func connectWasmHotReload() {
        let hotReloadSocket = URLSession.shared.webSocketTask(with: socketURL)
        hotReloadSocket.resume()
        
        func receiveHotReload() {
            hotReloadSocket.receive { [weak self] result in
                Task { @MainActor in
                    guard let self else { return }
                    if case .success(let message) = result, case .string(let text) = message {
                        if let data = text.data(using: .utf8),
                           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                           json["type"] as? String == "hot-reload-wasm",
                           let wasmPath = json["path"] as? String {
                            // Download and hot-swap the new WASM binary
                            Task {
                                do {
                                    let wasmURL = self.baseURL.appending(path: "bundle/artifact").appending(path: wasmPath)
                                    let (wasmData, _) = try await URLSession.shared.data(from: wasmURL)
                                    try self.wasmRuntime?.hotReloadWasm(wasmBytes: [UInt8](wasmData))
                                    if let renderJson = self.wasmRuntime?.renderWasm(),
                                       let renderData = renderJson.data(using: .utf8),
                                       let (node, newErrors) = try? FeatherRenderPayload.decode(renderData) {
                                        self.root = node
                                        self.errors = newErrors
                                    }
                                } catch {
                                    self.errors = ["Hot-reload failed: \(error.localizedDescription)"]
                                }
                            }
                        }
                    }
                    receiveHotReload()
                }
            }
        }
        receiveHotReload()
    }

    // MARK: - Bundle Fetch (HTTP)

    func fetchBundleRender() async {
        do {
            let manifestURL = baseURL.appending(path: "bundle/manifest")
            let (manifestData, _) = try await URLSession.shared.data(from: manifestURL)
            let manifest = try JSONDecoder().decode(BundleManifest.self, from: manifestData)

            guard manifest.sdkVersion == "1.0.0" else {
                root = .text(id: UUID().uuidString, text: "Unsupported SDK: \(manifest.sdkVersion)")
                return
            }
            guard manifest.protocolVersion == 1 else {
                root = .text(id: UUID().uuidString, text: "Unsupported protocol: \(manifest.protocolVersion)")
                return
            }

            // Cache delta information
            if let delta = manifest.delta {
                lastDelta = delta
            }

            let renderURL = baseURL
                .appending(path: "bundle/artifact")
                .appending(path: manifest.render.path)
            let (renderData, _) = try await URLSession.shared.data(from: renderURL)
            let (node, newErrors) = try FeatherRenderPayload.decode(renderData)
            root = node
            errors = newErrors
        } catch {
            root = .text(id: UUID().uuidString, text: "Bundle fetch failed: \(error.localizedDescription)")
            errors = [error.localizedDescription]
        }
    }

    // MARK: - WebSocket Connection

    func connectWebSocket() {
        socket = URLSession.shared.webSocketTask(with: socketURL)
        socket?.resume()
        isConnected = true
        reconnectAttempts = 0
        receiveNextMessage()
    }

    func disconnect() {
        socket?.cancel(with: .goingAway, reason: nil)
        socket = nil
        isConnected = false
        reconnectTimer?.invalidate()
    }

    func sendAction(action: String, payload: String?) {
        // ─── WASM Runtime Actions ─────────────────────────────────────────────
        if let runtime = wasmRuntime {
            if action.hasPrefix("host:") {
                handleHostAction(action)
                return
            }
            let event: [String: Any] = ["action": action, "payload": payload ?? NSNull()]
            if let data = try? JSONSerialization.data(withJSONObject: event),
               let jsonStr = String(data: data, encoding: .utf8) {
                runtime.applyActionEvent(payload: jsonStr)
            }
            return
        }

        // ─── Dev Server Actions ───────────────────────────────────────────────
        if action.hasPrefix("host:") {
            handleHostAction(action)
            return
        }
        let event = ActionEvent(type: "action", action: action, payload: payload)
        guard let data = try? JSONEncoder().encode(event),
              let text = String(data: data, encoding: .utf8) else {
            return
        }
        socket?.send(.string(text)) { _ in }
    }

    private func handleHostAction(_ action: String) {
        let command = action.replacingOccurrences(of: "host:", with: "")
        
        if command == "Haptics/vibrate" {
            #if os(iOS)
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            #endif
        } else if command.hasPrefix("Clipboard/setText=") {
            let value = String(command.dropFirst("Clipboard/setText=".count)).trimmingCharacters(in: CharacterSet(charactersIn: "\""))
            #if os(iOS)
            UIPasteboard.general.string = value
            #endif
        } else if command.hasPrefix("Clipboard/getText=") {
            let stateKey = String(command.dropFirst("Clipboard/getText=".count))
            #if os(iOS)
            let text = UIPasteboard.general.string ?? ""
            let escapedText = text.replacingOccurrences(of: "\"", with: "\\\"")
            sendAction(action: "\(stateKey)=\"\(escapedText)\"", payload: nil)
            #endif
        }
    }

    // MARK: - Private

    private func receiveNextMessage() {
        socket?.receive { [weak self] result in
            Task { @MainActor in
                guard let self else { return }

                switch result {
                case .success(let message):
                    switch message {
                    case .string(let payload):
                        if let data = payload.data(using: .utf8),
                           let (node, newErrors) = try? FeatherRenderPayload.decode(data) {
                            self.root = node
                            self.errors = newErrors
                        }
                    case .data(_):
                        break // Binary protocol reserved for future use
                    @unknown default:
                        break
                    }
                    self.receiveNextMessage()

                case .failure(let error):
                    self.isConnected = false
                    self.root = .text(
                        id: UUID().uuidString,
                        text: "Connection lost: \(error.localizedDescription)"
                    )
                    self.scheduleReconnect()
                }
            }
        }
    }

    private func scheduleReconnect() {
        guard reconnectAttempts < maxReconnectAttempts else {
            root = .text(
                id: UUID().uuidString,
                text: "Failed to reconnect after \(maxReconnectAttempts) attempts."
            )
            return
        }

        reconnectAttempts += 1
        let delay = Double(reconnectAttempts) * 2.0

        reconnectTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
            Task { @MainActor in
                self?.connectWebSocket()
            }
        }
    }
}

private struct ActionEvent: Encodable {
    let type: String
    let action: String
    let payload: String?
}
