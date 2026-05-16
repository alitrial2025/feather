import Foundation
import UIKit
import compression

@MainActor
final class FeatherDevClient: NSObject, ObservableObject {
    @Published var root: FeatherNode = .text(id: UUID().uuidString, text: "Waiting for Feather CLI...")
    @Published var isConnected: Bool = false
    @Published var lastDelta: BundleDelta? = nil
    @Published var errors: [String] = []

    // ─── Feather Runtime ─────────────────────────────────────────────────────
    private let runtime = AppRuntime()
    private var renderTimer: Timer?
    let moduleRegistry = ModuleRegistry()

    private var baseURL: URL
    private var socketURL: URL
    private var socket: URLSessionWebSocketTask?
    private var reconnectTimer: Timer?
    private let maxReconnectAttempts = 5
    private var reconnectAttempts = 0

    override init() {
        let lastIp = UserDefaults.standard.string(forKey: "last_ip") ?? "127.0.0.1"
        self.baseURL = URL(string: "http://\(lastIp):8080")!
        self.socketURL = URL(string: "ws://\(lastIp):8080/ws")!
        super.init()
        updateURLs(hostOrURL: lastIp)
        registerOfficialModules()
        setupLifecycleObservers()
    }

    // MARK: - App Initialization
    
    func start() {
        // Start the render/module polling loop (~60fps)
        startRenderLoop()

        // ─── Phase 1.5: Standalone Production Loading ───
        if loadStandaloneBundle() {
            return
        }

        // ─── Priority 0: Production Bundle URL (Environment) ──────────────────
        if let bundleUrlStr = ProcessInfo.processInfo.environment["FEATHER_BUNDLE_URL"],
           let bundleUrl = URL(string: bundleUrlStr) {
            Task {
                await fetchBundleFromURL(bundleUrl)
            }
            return
        }

        // ─── Priority 1: WASM Runtime ─────────────────────────────────────────
        if let wasmUrl = Bundle.main.url(forResource: "app", withExtension: "wasm", subdirectory: "feather"),
           let wasmData = try? Data(contentsOf: wasmUrl) {
            do {
                try runtime.loadWasm(wasmBytes: [UInt8](wasmData))
                isConnected = true

                // Initial render
                if let json = runtime.renderWasm(),
                   let data = json.data(using: .utf8),
                   let (node, newErrors) = try? FeatherRenderPayload.decode(data) {
                    root = node
                    errors = newErrors
                }
                return
            } catch {
                errors = ["WASM load failed: \(error.localizedDescription)"]
            }
        }

        // ─── Priority 2: AOT JSON Bundle ──────────────────────────────────────
        if let localUrl = Bundle.main.url(forResource: "app.feather", withExtension: "json"),
           let data = try? Data(contentsOf: localUrl) {
            do {
                let jsonPayload = String(data: data, encoding: .utf8) ?? "{}"
                let resolved = runtime.resolveStateTextNodes(jsonPayload: jsonPayload)
                if let resolvedData = resolved.data(using: .utf8) {
                    let (node, newErrors) = try FeatherRenderPayload.decode(resolvedData)
                    root = node
                    errors = newErrors
                }
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

    func updateURLs(hostOrURL: String) {
        let trimmed = hostOrURL.trimmingCharacters(in: .whitespacesAndNewlines)
        UserDefaults.standard.set(trimmed, forKey: "last_ip")
        
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            guard let url = URL(string: trimmed) else { return }
            let scheme = url.scheme ?? "http"
            let host = url.host ?? "127.0.0.1"
            let port = url.port != nil ? ":\(url.port!)" : ""
            
            self.baseURL = URL(string: "\(scheme)://\(host)\(port)")!
            let wsScheme = scheme == "https" ? "wss" : "ws"
            self.socketURL = URL(string: "\(wsScheme)://\(host)\(port)/ws")!
        } else {
            self.baseURL = URL(string: "http://\(trimmed):8080")!
            self.socketURL = URL(string: "ws://\(trimmed):8080/ws")!
        }
    }

    func resetAndConnect() {
        errors = []
        root = .text(id: UUID().uuidString, text: "Connecting to \(baseURL.absoluteString)...")
        disconnect()
        connectWebSocket()
        Task {
            await fetchBundleRender()
        }
    }

    private func loadStandaloneBundle() -> Bool {
        // 1. Try WASM logic
        if let wasmUrl = Bundle.main.url(forResource: "app", withExtension: "wasm", subdirectory: "feather"),
           let wasmData = try? Data(contentsOf: wasmUrl) {
            try? runtime.loadWasm(wasmBytes: [UInt8](wasmData))
        }

        // 2. Try Binary IR (MessagePack)
        if let mpUrl = Bundle.main.url(forResource: "app.feather", withExtension: "mp", subdirectory: "feather"),
           let data = try? Data(contentsOf: mpUrl) {
            let (node, newErrors) = FeatherRenderPayload.decodeMessagePack(data)
            self.root = node
            self.errors = newErrors
            isConnected = true
            return true
        }

        // 3. Try JSON IR fallback
        if let jsonUrl = Bundle.main.url(forResource: "app.feather", withExtension: "json", subdirectory: "feather"),
           let data = try? Data(contentsOf: jsonUrl) {
            let jsonPayload = String(data: data, encoding: .utf8) ?? "{}"
            let resolved = runtime.resolveStateTextNodes(jsonPayload: jsonPayload)
            if let resolvedData = resolved.data(using: .utf8),
               let (node, newErrors) = try? FeatherRenderPayload.decode(resolvedData) {
                self.root = node
                self.errors = newErrors
                isConnected = true
                return true
            }
        }

        return false
    }

    // MARK: - Render & Module Loop

    private func setupLifecycleObservers() {
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.runtime.pushNativeEvent(name: "onResume", payload: "{}")
        }
        NotificationCenter.default.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.runtime.pushNativeEvent(name: "onPause", payload: "{}")
        }
        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.runtime.pushNativeEvent(name: "onStop", payload: "{}")
        }
    }

    private func startRenderLoop() {
        renderTimer?.invalidate()
        renderTimer = Timer.scheduledTimer(withTimeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }

                // Drain console logs
                let logs = self.runtime.drainConsoleLogs()
                let errorLogs = logs.filter { $0.localizedCaseInsensitiveContains("error") || $0.localizedCaseInsensitiveContains("exception") }
                if !errorLogs.isEmpty {
                    self.errors = errorLogs
                }

                if self.runtime.pollRenderNeeded() {
                    // Drain module calls first
                    self.drainModuleCalls()

                    if self.runtime.hasWasm() {
                        if let renderTree = self.runtime.renderWasm() {
                            if renderTree.hasPrefix("base64:") {
                                let base64Str = String(renderTree.dropFirst(7))
                                if let data = Data(base64Encoded: base64Str) {
                                    let (node, newErrors) = FeatherRenderPayload.decodeMessagePack(data)
                                    self.root = node
                                    self.errors = newErrors
                                }
                            } else if let data = renderTree.data(using: .utf8),
                                      let (node, newErrors) = try? FeatherRenderPayload.decode(data) {
                                self.root = node
                                self.errors = newErrors
                            }
                        }
                    }
                }
            }
        }
    }
    private func drainModuleCalls() {
        let calls = runtime.drainModuleCalls()
        for callJson in calls {
            if let data = callJson.data(using: .utf8),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                handleStructuredModuleCall(json)
            }
        }
    }

    private func handleStructuredModuleCall(_ json: [String: Any]) {
        guard let moduleName = json["module"] as? String,
              let method = json["method"] as? String else { return }
        
        let payload = json["payload"] as? [String: Any] ?? [:]
        let resultStateKey = json["resultStateKey"] as? String
        
        var context = moduleContext()
        if let key = resultStateKey, !key.isEmpty {
            context = FeatherModuleContext(urlSession: .shared) { _, value in
                self.sendStateAssignment(key, value)
            }
        }
        
        if !moduleRegistry.call(moduleName: moduleName, method: method, payload: payload, context: context) {
            print("Module call failed: \(moduleName)/\(method)")
        }
    }

    private func fetchBundleFromURL(_ url: URL) async {
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let jsonPayload = String(data: data, encoding: .utf8) ?? "{}"
            let resolved = runtime.resolveStateTextNodes(jsonPayload: jsonPayload)
            if let resolvedData = resolved.data(using: .utf8) {
                let (node, newErrors) = try FeatherRenderPayload.decode(resolvedData)
                root = node
                errors = newErrors
            }
            isConnected = true
        } catch {
            root = .text(id: UUID().uuidString, text: "Failed to load production bundle: \(error.localizedDescription)")
            errors = [error.localizedDescription]
        }
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

            if let delta = manifest.delta {
                lastDelta = delta
            }

            let requiredModules = manifest.modules?.map { moduleEntry in
                BundleModule(
                    name: moduleEntry.name,
                    version: moduleEntry.version,
                    platforms: moduleEntry.platforms ?? [],
                    permissions: moduleEntry.permissions ?? []
                )
            } ?? []
            let missingModules = runtime.checkRequirements(requiredModules: requiredModules, hostPlatform: "ios")

            // Fetch WASM if present (prefer compressed)
            if let wasmArtifact = manifest.wasm {
                let wasmURL = baseURL
                    .appending(path: "bundle/artifact")
                    .appending(path: wasmArtifact.path)
                if let wasmData = try? await tryFetchCompressed(url: wasmURL) {
                    try? runtime.loadWasm(wasmBytes: [UInt8](wasmData))
                }
            let renderPath = manifest.render.path
            let mpPath = renderPath.replacingOccurrences(of: ".json", with: ".mp")
            let mpURL = baseURL.appending(path: "bundle/artifact").appending(path: mpPath)

            // Try MessagePack first
            if let mpData = try? await tryFetchCompressed(url: mpURL) {
                let (node, newErrors) = FeatherRenderPayload.decodeMessagePack(mpData)
                self.root = node
                self.errors = newErrors + missingModules
                isConnected = true
            } else {
                // Fallback to JSON
                let renderURL = baseURL
                    .appending(path: "bundle/artifact")
                    .appending(path: renderPath)

                if let renderData = try? await tryFetchCompressed(url: renderURL),
                   let jsonPayload = String(data: renderData, encoding: .utf8) {

                    let finalRenderPayload = runtime.hasWasm() ? (runtime.renderWasm() ?? jsonPayload) : jsonPayload

                    if finalRenderPayload.hasPrefix("base64:") {
                        let base64Str = String(finalRenderPayload.dropFirst(7))
                        if let data = Data(base64Encoded: base64Str) {
                            let (node, newErrors) = FeatherRenderPayload.decodeMessagePack(data)
                            self.root = node
                            self.errors = newErrors + missingModules
                        }
                    } else {
                        let resolved = runtime.resolveStateTextNodes(jsonPayload: finalRenderPayload)
                        if let resolvedData = resolved.data(using: .utf8) {
                            let (node, newErrors) = try FeatherRenderPayload.decode(resolvedData)
                            self.root = node
                            self.errors = newErrors + missingModules
                        }
                    }
                    isConnected = true
                }
            }

                        allErrors.append("Missing required module (from manifest): \(name)")
                    }
                    errors = allErrors
                }
            }
        } catch {
            root = .text(id: UUID().uuidString, text: "Bundle fetch failed: \(error.localizedDescription)")
            errors = [error.localizedDescription]
        }
    }

    private func tryFetchCompressed(url: URL) async throws -> Data {
        let gzURL = url.appendingPathExtension("gz")
        if let (data, response) = try? await URLSession.shared.data(from: gzURL),
           let httpResponse = response as? HTTPURLResponse,
           httpResponse.statusCode == 200 {
            // Decompress
            return try decompressGzip(data)
        }
        
        let (data, _) = try await URLSession.shared.data(from: url)
        return data
    }

    private func decompressGzip(_ data: Data) throws -> Data {
        let size = 8 * 1024 * 1024 // 8MB max for now
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: size)
        defer { buffer.deallocate() }
        
        return try data.withUnsafeBytes { (ptr: UnsafeRawBufferPointer) -> Data in
            guard let baseAddress = ptr.baseAddress?.assumingMemoryBound(to: UInt8.self) else {
                throw NSError(domain: "Feather", code: -1, userInfo: [NSLocalizedDescriptionKey: "Pointer error"])
            }
            let decompressedSize = compression_decode_buffer(buffer, size, baseAddress, data.count, nil, COMPRESSION_ZLIB)
            if decompressedSize == 0 {
                throw NSError(domain: "Feather", code: -1, userInfo: [NSLocalizedDescriptionKey: "Decompression failed"])
            }
            return Data(bytes: buffer, count: decompressedSize)
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
        // Feed action into Core first
        let eventObj: [String: Any] = [
            "action": action,
            "payload": payload ?? NSNull()
        ]
        if let data = try? JSONSerialization.data(withJSONObject: eventObj),
           let jsonStr = String(data: data, encoding: .utf8) {
            runtime.applyActionEvent(payload: jsonStr)
        }

        if action.hasPrefix("host:") {
            handleHostAction(action)
            return
        }

        // Also forward to dev server for CLI sync
        var socketEvent = eventObj
        socketEvent["type"] = "action"
        if let data = try? JSONSerialization.data(withJSONObject: socketEvent),
           let text = String(data: data, encoding: .utf8) {
            socket?.send(.string(text)) { _ in }
        }
    }

    private func handleHostAction(_ action: String) {
        let command = action.replacingOccurrences(of: "host:", with: "")
        if moduleRegistry.handleLegacyCommand(command, context: moduleContext()) {
            return
        }
        print("Unhandled host command: \(command)")
    }

    private func registerOfficialModules() {
        moduleRegistry.register(DeviceModule())
        moduleRegistry.register(NetworkModule())
        moduleRegistry.register(StorageModule())
        moduleRegistry.register(NavigationModule(onNavigateAction: self.sendAction))
        moduleRegistry.register(MediaModule())
        moduleRegistry.register(WebViewModule())
        moduleRegistry.register(FilesModule())
        moduleRegistry.register(LocationModule())
        moduleRegistry.register(AuthModule())
        moduleRegistry.register(NotificationsModule())
        moduleRegistry.register(MapsModule())
        moduleRegistry.register(PaymentsModule())
        moduleRegistry.register(ReflectionModule())
        moduleRegistry.register(CameraModuleIOS())
        moduleRegistry.register(MapsModuleIOS())

        // Register with Rust runtime

        for module in moduleRegistry.registeredModules() {
            runtime.registerModule(name: module.name, module: NativeModuleWrapper(module: module, context: moduleContext()))
        }
        
        moduleRegistry.initializeAll(context: moduleContext())
    }

    private func moduleContext() -> FeatherModuleContext {
        return FeatherModuleContext(urlSession: .shared) { key, value in
            self.sendStateAssignment(key, value)
        }
    }

    private func sendStateAssignment(_ key: String, _ value: String) {
        let escaped = value.replacingOccurrences(of: "\\", with: "\\\\")
                           .replacingOccurrences(of: "\"", with: "\\\"")
        sendAction(action: "\(key)=\"\(escaped)\"", payload: nil)
    }

    // MARK: - Private

    private func receiveNextMessage() {
        socket?.receive { [weak self] result in
            Task { @MainActor in
                guard let self else { return }

                switch result {
                case .success(let message):
                    if case .string(let payload) = message,
                       let data = payload.data(using: .utf8),
                       let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                        
                        let type = json["type"] as? String
                        if type == "module_call" {
                            self.runtime.applyActionEvent(payload: payload)
                        } else if type == "state_update" {
                            if let key = json["key"] as? String,
                               let value = json["value"] {
                                self.runtime.setState(key: key, value: "\(value)")
                            }
                        } else if type == "hot-reload-wasm" {
                            if let path = json["path"] as? String {
                                self.fetchWasmAndReload(path: path)
                            }
                        } else {
                            let resolved = self.runtime.resolveStateTextNodes(jsonPayload: payload)
                            if let resolvedData = resolved.data(using: .utf8),
                               let (node, newErrors) = try? FeatherRenderPayload.decode(resolvedData) {
                                self.root = node
                                self.errors = newErrors
                            }
                        }
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

    private func fetchWasmAndReload(path: String) {
        Task {
            do {
                let wasmURL = baseURL
                    .appending(path: "bundle/artifact")
                    .appending(path: path)
                let data = try await tryFetchCompressed(url: wasmURL)
                try runtime.hotReloadWasm(wasmBytes: [UInt8](data))
            } catch {
                print("Hot reload failed: \(error.localizedDescription)")
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
