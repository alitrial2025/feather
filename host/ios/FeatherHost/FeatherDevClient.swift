import Foundation
import UIKit
import Compression

@MainActor
final class FeatherDevClient: NSObject, ObservableObject {
    @Published var root: FeatherNode = .text(id: UUID().uuidString, text: "Waiting for Feather CLI...")
    @Published var isConnected: Bool = false
    @Published var lastDelta: BundleDelta? = nil
    @Published var errors: [String] = []

    // ─── Feather Runtime ─────────────────────────────────────────────────────
    private var runtime = AppRuntime()
    private var renderTimer: Timer?
    private var lifecycleObservers: [NSObjectProtocol] = []
    private var bundleTask: Task<Void, Never>?
    private(set) var moduleRegistry = ModuleRegistry()

    private var baseURL: URL
    private var socketURL: URL
    private var socket: URLSessionWebSocketTask?
    private var projectPublicKey: String?
    private var reconnectTimer: Timer?
    private let maxReconnectAttempts = 5
    private var reconnectAttempts = 0
    private var bundleFetchGeneration: UInt64 = 0
    private var currentJsonMessage: [String: Any]? = nil
    private var diagnosticBundle: [String: Any]?
    private var diagnosticURL: URL?
    private var standaloneLinks: [[String: Any]] = []
    private var standaloneJavascript = false
    var canManageProject: Bool { !standaloneJavascript }
    var projectEngine: String {
        switch runtimeName {
        case "kotlin-js": return "Kotlin/JS"
        case "wasm": return "WebAssembly"
        default: return "Layout"
        }
    }
    private var runtimeName: String {
        if runtime.hasJavascript() { return "kotlin-js" }
        if runtime.hasWasm() { return "wasm" }
        return "layout"
    }
    var projectModules: [ModuleInfo] { runtime.getRegisteredModules().sorted { $0.name < $1.name } }

    deinit {
        renderTimer?.invalidate()
        reconnectTimer?.invalidate()
        bundleTask?.cancel()
        socket?.cancel(with: .goingAway, reason: nil)
        lifecycleObservers.forEach { NotificationCenter.default.removeObserver($0) }
    }

    func openAppLink(_ url: URL) {
        guard let path = appLinkPath(url, links: standaloneLinks),
              let event = try? JSONSerialization.data(withJSONObject: ["action": "__deepLink", "payload": path]),
              let text = String(data: event, encoding: .utf8) else { return }
        runtime.applyActionEvent(payload: text)
    }

    private func loadJavascript(manifest: String, source: String, key: String, url: URL? = nil) throws {
        forwardConsoleLogs()
        do {
            try runtime.loadSignedJavascript(manifestJson: manifest, source: source, publicKey: key)
        } catch {
            publishDiagnostic(phase: "load", level: "error", message: diagnosticMessage(error), url: url, bundle: nil)
            throw error
        }
        let value = try JSONSerialization.jsonObject(with: Data(manifest.utf8)) as? [String: Any] ?? [:]
        let metadata = value["metadata"] as? [String: Any]
        let artifact = value["javascript"] as? [String: Any]
        var identity: [String: Any] = [
            "app_id": metadata?["app_id"] as? String ?? value["name"] as? String ?? "",
            "bundle_version": value["bundle_version"] as? Int ?? 0,
            "sha256": artifact?["sha256"] as? String ?? ""
        ]
        identity["app_version"] = metadata?["version_name"] as? String
        diagnosticBundle = identity
        diagnosticURL = url
    }

    private func renderJavascript() throws -> String {
        let document: String
        do {
            document = try runtime.renderJavascriptDocument()
        } catch {
            for event in runtime.drainDiagnostics() {
                reportDiagnostic(phase: "runtime", level: event.level, message: event.message)
            }
            reportDiagnostic(phase: "render", level: "error", message: diagnosticMessage(error))
            throw error
        }
        let payload = try JSONSerialization.jsonObject(with: Data(document.utf8)) as? [String: Any]
        for event in payload?["diagnostics"] as? [[String: String]] ?? [] {
            if let message = event["message"] {
                reportDiagnostic(phase: "runtime", level: event["level"] ?? "error", message: message)
            }
        }
        return document
    }

    private func diagnosticMessage(_ error: Error) -> String {
        if let runtimeError = error as? ModuleError, case let .ExecutionFailed(message) = runtimeError {
            return message
        }
        return error.localizedDescription
    }

    private func reportDiagnostic(phase: String, level: String, message: String) {
        publishDiagnostic(phase: phase, level: level, message: message, url: diagnosticURL, bundle: diagnosticBundle)
    }

    private func publishDiagnostic(phase: String, level: String, message: String, url: URL?, bundle: [String: Any]?) {
        if url == nil && (Bundle.main.object(forInfoDictionaryKey: "FeatherProductionDiagnostics") as? Bool) != true {
            return
        }
        var event: [String: Any] = [
            "type": "diagnostic",
            "schema_version": 1,
            "platform": "ios",
            "host": [
                "id": Bundle.main.bundleIdentifier ?? "",
                "version": Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "",
                "build": Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
            ],
            "phase": phase,
            "level": level,
            "message": String(message.prefix(16_384))
        ]
        event["bundle"] = bundle
        if let url {
            if url == baseURL { sendLogEvent(event) }
        } else if let data = try? JSONSerialization.data(withJSONObject: event),
                  let text = String(data: data, encoding: .utf8) {
            NSLog("FeatherDiagnostics %@", text)
        }
    }

    private func sendLogEvent(_ event: [String: Any]) {
        #if DEBUG
        if let socket, let data = try? JSONSerialization.data(withJSONObject: event),
           let text = String(data: data, encoding: .utf8) {
            socket.send(.string(text)) { _ in }
        }
        #endif
    }

    private func forwardConsoleLogs() {
        let messages = runtime.drainConsoleLogs()
        guard diagnosticBundle == nil || diagnosticURL == baseURL else { return }
        for message in messages {
            sendLogEvent(["type": "log", "platform": "ios", "message": message])
        }
    }

    override init() {
        let lastIp = UserDefaults.standard.string(forKey: "last_ip") ?? "127.0.0.1"
        self.baseURL = URL(string: "http://\(lastIp):8080") ?? URL(string: "http://127.0.0.1:8080")!
        self.socketURL = URL(string: "ws://\(lastIp):8080/ws") ?? URL(string: "ws://127.0.0.1:8080/ws")!
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
                try runtime.loadWasm(wasmBytes: wasmData)
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
        requestBundle()
    }

    @discardableResult
    func updateURLs(hostOrURL: String) -> Bool {
        #if DEBUG
        let allowProjectKey = true
        #else
        let allowProjectKey = false
        #endif
        guard let project = ProjectConnection.parse(hostOrURL, allowProjectKey: allowProjectKey) else {
            return false
        }
        bundleFetchGeneration += 1
        baseURL = project.baseURL
        socketURL = project.socketURL
        projectPublicKey = project.publicKey
        UserDefaults.standard.set(hostOrURL.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "last_ip")
        return true
    }

    func resetAndConnect() {
        guard !standaloneJavascript else { return }
        standaloneLinks = []
        bundleFetchGeneration += 1
        startRenderLoop()
        reconnectAttempts = 0
        errors = []
        root = .text(id: UUID().uuidString, text: "Connecting to \(baseURL.absoluteString)...")
        disconnect()
        connectWebSocket()
        requestBundle()
    }

    private func loadStandaloneBundle() -> Bool {
        if let url = Bundle.main.url(forResource: "app", withExtension: "js", subdirectory: "feather") {
            standaloneJavascript = true
            do {
                guard let manifestURL = Bundle.main.url(forResource: "manifest", withExtension: "json", subdirectory: "feather") else {
                    throw NSError(domain: "Feather", code: 403, userInfo: [NSLocalizedDescriptionKey: "Missing signed manifest"])
                }
                let key = Bundle.main.object(forInfoDictionaryKey: "FeatherBundlePublicKey") as? String ?? ""
                let manifest = try String(contentsOf: manifestURL, encoding: .utf8)
                runtime.setState(key: "host_standalone", jsonValue: "true")
                try loadJavascript(manifest: manifest,
                    source: String(contentsOf: url, encoding: .utf8), key: key.trimmingCharacters(in: .whitespacesAndNewlines))
                let document = try renderJavascript()
                let data = Data(document.utf8)
                let (node, diagnostics) = try FeatherRenderPayload.decode(data)
                currentJsonMessage = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                root = node
                errors = diagnostics
                isConnected = true
                let value = try JSONSerialization.jsonObject(with: Data(manifest.utf8)) as? [String: Any]
                standaloneLinks = value?["deep_links"] as? [[String: Any]] ?? []
            } catch {
                root = .text(id: UUID().uuidString, text: "Bundle verification or startup failed")
                errors = [error.localizedDescription]
            }
            return true // Rejected signed code must not fall back to unsigned layouts.
        }

        // 1. Try WASM logic
        if let wasmUrl = Bundle.main.url(forResource: "app", withExtension: "wasm", subdirectory: "feather"),
           let wasmData = try? Data(contentsOf: wasmUrl) {
            try? runtime.loadWasm(wasmBytes: wasmData)
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
                if let dict = try? JSONSerialization.jsonObject(with: resolvedData, options: []) as? [String: Any] {
                    self.currentJsonMessage = dict
                }
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
        lifecycleObservers.append(NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.runtime.pushNativeEvent(name: "onResume", payload: "{}")
        })
        lifecycleObservers.append(NotificationCenter.default.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.runtime.pushNativeEvent(name: "onPause", payload: "{}")
        })
        lifecycleObservers.append(NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.runtime.pushNativeEvent(name: "onStop", payload: "{}")
        })
    }

    private func startRenderLoop() {
        renderTimer?.invalidate()
        renderTimer = Timer.scheduledTimer(withTimeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }

                self.forwardConsoleLogs()

                if self.runtime.pollRenderNeeded() {
                    // Drain module calls first
                    self.drainModuleCalls()

                    if self.runtime.hasJavascript() {
                        do {
                            let data = Data(try self.renderJavascript().utf8)
                            let (node, diagnostics) = try FeatherRenderPayload.decode(data)
                            self.currentJsonMessage = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                            self.root = node
                            self.errors = diagnostics
                        } catch { self.errors = [error.localizedDescription] }
                    } else if self.runtime.hasWasm() {
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
                                if let dict = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                                    self.currentJsonMessage = dict
                                }
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
        if moduleName == "feather-device", method == "hasModule", let key = resultStateKey {
            let available = runtime.getRegisteredModules().contains { $0.name == (payload["moduleName"] as? String) }
            sendStateAssignment(key, available ? "true" : "false")
            return
        }
        if moduleName == "feather-device", method == "getRuntimeInfo", let key = resultStateKey {
            let modules = Dictionary(uniqueKeysWithValues: projectModules.map { ($0.name, $0.version) })
            let info: [String: Any] = ["runtime": runtimeName, "modules": modules]
            if let data = try? JSONSerialization.data(withJSONObject: info),
               let value = String(data: data, encoding: .utf8) {
                sendStateAssignment(key, value)
            }
            return
        }
        
        let baseContext = moduleContext()
        var context = baseContext
        if let key = resultStateKey, !key.isEmpty {
            context = FeatherModuleContext(urlSession: .shared) { _, value in
                baseContext.sendStateAssignment(key, value)
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
                if let dict = try? JSONSerialization.jsonObject(with: resolvedData, options: []) as? [String: Any] {
                    self.currentJsonMessage = dict
                }
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
        bundleFetchGeneration += 1
        let generation = bundleFetchGeneration
        let projectURL = baseURL
        let pinnedKey = Bundle.main.object(forInfoDictionaryKey: "FeatherBundlePublicKey") as? String ?? ""
        let publicKey = projectPublicKey ?? pinnedKey
        do {
            let manifestURL = projectURL.appending(path: "bundle/manifest")
            let (manifestData, _) = try await URLSession.shared.data(from: manifestURL)
            guard !Task.isCancelled, generation == bundleFetchGeneration else { return }
            let manifest = try JSONDecoder().decode(BundleManifest.self, from: manifestData)

            // ─── Feature 1: Cryptographic Bundle Verification & Code Signing ───
            let signature = manifest.signature ?? ""
            let manifestJSON = String(data: manifestData, encoding: .utf8) ?? "{}"
            let hostPublicKeyB64 = publicKey.trimmingCharacters(in: .whitespacesAndNewlines)
            
            if signature.isEmpty || !verifyManifestSignature(manifestJson: manifestJSON, signatureB64: signature, publicKeyB64: hostPublicKeyB64) {
                throw NSError(domain: "Feather", code: 403, userInfo: [NSLocalizedDescriptionKey: "CRYPTOGRAPHIC SECURE BOOT FAULT"])
            }

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

            if manifest.runtime == "kotlin-js" {
                guard manifest.javascript?.path == "app.js" else {
                    throw NSError(domain: "Feather", code: 403, userInfo: [NSLocalizedDescriptionKey:"Invalid JavaScript artifact"])
                }
                let (sourceData, response) = try await URLSession.shared.data(from: projectURL.appending(path: "bundle/artifact/app.js"))
                guard let http = response as? HTTPURLResponse, http.statusCode == 200,
                      let source = String(data: sourceData, encoding: .utf8) else {
                    throw NSError(domain: "Feather", code: 502, userInfo: [NSLocalizedDescriptionKey:"JavaScript download failed"])
                }
                guard !Task.isCancelled, generation == bundleFetchGeneration else { return }
                try loadJavascript(manifest: manifestJSON, source: source, key: hostPublicKeyB64, url: projectURL)
                let data = Data(try renderJavascript().utf8)
                let (node, diagnostics) = try FeatherRenderPayload.decode(data)
                currentJsonMessage = try JSONSerialization.jsonObject(with: data) as? [String:Any]
                root = node
                errors = diagnostics + missingModules
                isConnected = true
                return
            }

            // Fetch WASM if present (prefer compressed)
            if let wasmArtifact = manifest.wasm {
                let wasmURL = projectURL
                    .appending(path: "bundle/artifact")
                    .appending(path: wasmArtifact.path)
                if let wasmData = try? await tryFetchCompressed(url: wasmURL) {
                    guard !Task.isCancelled, generation == bundleFetchGeneration else { return }
                    try? runtime.loadWasm(wasmBytes: wasmData)
                }
            }

            let renderPath = manifest.render.path
            let mpPath = renderPath.replacingOccurrences(of: ".json", with: ".mp")
            let mpURL = projectURL.appending(path: "bundle/artifact").appending(path: mpPath)

            // Try MessagePack first
            if let mpData = try? await tryFetchCompressed(url: mpURL) {
                guard !Task.isCancelled, generation == bundleFetchGeneration else { return }
                let (node, newErrors) = FeatherRenderPayload.decodeMessagePack(mpData)
                self.root = node
                self.errors = newErrors + missingModules
                isConnected = true
            } else {
                // Fallback to JSON
                guard !Task.isCancelled, generation == bundleFetchGeneration else { return }
                let renderURL = projectURL
                    .appending(path: "bundle/artifact")
                    .appending(path: renderPath)

                if let renderData = try? await tryFetchCompressed(url: renderURL),
                   let jsonPayload = String(data: renderData, encoding: .utf8) {
                    guard !Task.isCancelled, generation == bundleFetchGeneration else { return }

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
                            if let dict = try? JSONSerialization.jsonObject(with: resolvedData, options: []) as? [String: Any] {
                                self.currentJsonMessage = dict
                            }
                            let (node, newErrors) = try FeatherRenderPayload.decode(resolvedData)
                            self.root = node
                            self.errors = newErrors + missingModules
                        }
                    }
                    isConnected = true
                }
            }
        } catch {
            guard !Task.isCancelled, generation == bundleFetchGeneration else { return }
            let nsError = error as NSError
            if nsError.localizedDescription == "CRYPTOGRAPHIC SECURE BOOT FAULT" {
                root = .text(id: UUID().uuidString, text: "CRYPTOGRAPHIC SECURE BOOT FAULT")
                errors = [
                    "CRITICAL SECURITY ALERT: CRYPTOGRAPHIC SECURE BOOT FAULT",
                    "The dynamic bundle signature could not be verified against the host public key.",
                    "Execution halted to prevent unauthorized remote code execution."
                ]
            } else {
                root = .text(id: UUID().uuidString, text: "Bundle fetch failed: \(error.localizedDescription)")
                errors = [error.localizedDescription]
            }
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
        receiveNextMessage()
    }

    func disconnect() {
        bundleFetchGeneration += 1
        bundleTask?.cancel()
        bundleTask = nil
        socket?.cancel(with: .goingAway, reason: nil)
        socket = nil
        isConnected = false
        reconnectTimer?.invalidate()
    }

    func closeProject() {
        disconnect()
        renderTimer?.invalidate()
        renderTimer = nil
        moduleRegistry.onStop()
        moduleRegistry.onDestroy()
        currentJsonMessage = nil
        diagnosticBundle = nil
        diagnosticURL = nil
        lastDelta = nil
        root = .text(id: UUID().uuidString, text: "Waiting for Feather CLI...")
        runtime = AppRuntime()
        moduleRegistry = ModuleRegistry()
        registerOfficialModules()
        errors = []
    }

    func refreshProject() {
        if isConnected { requestBundle() } else { resetAndConnect() }
    }

    private func requestBundle() {
        bundleTask?.cancel()
        bundleTask = Task { [weak self] in
            guard let self else { return }
            await self.fetchBundleRender()
        }
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

        if runtime.hasJavascript() {
            if let data = runtime.getAllState().data(using: .utf8),
               let state = try? JSONSerialization.jsonObject(with: data),
               let event = try? JSONSerialization.data(withJSONObject: ["type":"runtime_state", "state":state]),
               let text = String(data: event, encoding: .utf8) {
                socket?.send(.string(text)) { _ in }
            }
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
        moduleRegistry.register(DeviceInfoModule())
        moduleRegistry.register(NetworkModule())
        moduleRegistry.register(StorageModule())
        moduleRegistry.register(NavigationModule { [weak self] action, payload in
            self?.sendAction(action: action, payload: payload)
        })
        moduleRegistry.register(MediaModule())
        moduleRegistry.register(WebViewModule())
        moduleRegistry.register(FilesModule())
        moduleRegistry.register(LocationModule())
        moduleRegistry.register(AuthModule())
        moduleRegistry.register(NotificationsModule())
        moduleRegistry.register(MapsModule())
        moduleRegistry.register(PaymentsModule())
        moduleRegistry.register(ReflectionModule())
        // External sample camera/maps plugins are opt-in host dependencies.

        // Load dynamic third-party plugins
        let dynamicPlugins = FeatherPluginLoader.loadPlugins()
        for plugin in dynamicPlugins {
            moduleRegistry.register(plugin)
            print("FeatherPluginLoader: Registered dynamic plugin: \(plugin.name)")
        }

        // Register with Rust runtime

        for module in moduleRegistry.registeredModules() {
            runtime.registerModule(name: module.name, module: NativeModuleWrapper(module: module, context: moduleContext()))
        }
        
        moduleRegistry.initializeAll(context: moduleContext())
    }

    private func moduleContext() -> FeatherModuleContext {
        let engine = runtime
        return FeatherModuleContext(urlSession: .shared) { [weak self, weak engine] key, value in
            guard let self, let engine, self.runtime === engine else { return }
            self.sendStateAssignment(key, value)
        }
    }

    private func sendStateAssignment(_ key: String, _ value: String) {
        guard !key.isEmpty else { return }
        runtime.setState(key: key, jsonValue: value)
        let typed = value.data(using: .utf8).flatMap { try? JSONSerialization.jsonObject(with: $0, options: .fragmentsAllowed) } ?? value
        if let data = try? JSONSerialization.data(withJSONObject: ["type":"state_update", "key":key, "value":typed]),
           let text = String(data: data, encoding: .utf8) {
            socket?.send(.string(text)) { _ in }
        }
    }

    // MARK: - Private

    private func receiveNextMessage() {
        guard let connection = socket else { return }
        connection.receive { [weak self, weak connection] result in
            Task { @MainActor in
                guard let self, let connection, self.socket === connection else { return }

                switch result {
                case .success(let message):
                    self.isConnected = true
                    if self.reconnectAttempts > 0 {
                        self.reconnectAttempts = 0
                        self.requestBundle()
                    }
                    if case .string(let payload) = message,
                       let data = payload.data(using: .utf8),
                       let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                        
                        let type = json["type"] as? String
                        if type == "hot-reload-js" {
                            self.requestBundle()
                        } else if type == "devtools_state_update" {
                            if let key = json["key"] as? String, let value = json["value"],
                               let data = try? JSONSerialization.data(withJSONObject: value, options: .fragmentsAllowed),
                               let jsonValue = String(data: data, encoding: .utf8) {
                                self.runtime.setState(key: key, jsonValue: jsonValue)
                            }
                        } else if self.runtime.hasJavascript() && ["render", "patch", "state_update", "module_call"].contains(type ?? "") {
                            // Device runtime owns its state; server output is a preview.
                        } else if self.runtime.hasJavascript() && type == "render_error" {
                            self.errors = (json["diagnostics"] as? [[String:Any]] ?? []).compactMap { $0["message"] as? String }
                        } else if type == "module_call" {
                            self.runtime.applyActionEvent(payload: payload)
                        } else if type == "state_update" {
                            if let key = json["key"] as? String,
                               let value = json["value"] {
                                self.runtime.setState(key: key, jsonValue: "\(value)")
                            }
                        } else if type == "hot-reload-wasm" {
                            if let path = json["path"] as? String {
                                self.fetchWasmAndReload(path: path)
                            }
                        } else if type == "patch" {
                            if var activeJson = self.currentJsonMessage,
                               let patchesArray = json["patches"] as? [[String: Any]] {
                                for patch in patchesArray {
                                    self.applySinglePatch(to: &activeJson, patch: patch)
                                }
                                self.currentJsonMessage = activeJson
                                if let resolvedData = try? JSONSerialization.data(withJSONObject: activeJson, options: []),
                                   let jsonString = String(data: resolvedData, encoding: .utf8) {
                                    let resolved = self.runtime.resolveStateTextNodes(jsonPayload: jsonString)
                                    if let finalData = resolved.data(using: .utf8),
                                       let (node, newErrors) = try? FeatherRenderPayload.decode(finalData) {
                                        self.root = node
                                        self.errors = newErrors
                                    }
                                }
                            }
                        } else {
                            let resolved = self.runtime.resolveStateTextNodes(jsonPayload: payload)
                            if let resolvedData = resolved.data(using: .utf8),
                               let (node, newErrors) = try? FeatherRenderPayload.decode(resolvedData) {
                                if let dict = try? JSONSerialization.jsonObject(with: resolvedData, options: []) as? [String: Any] {
                                    self.currentJsonMessage = dict
                                }
                                self.root = node
                                self.errors = newErrors
                            }
                        }
                    }
                    self.receiveNextMessage()

                case .failure:
                    self.isConnected = false
                    self.errors = ["Development server disconnected. Reconnecting…"]
                    self.scheduleReconnect()
                }
            }
        }
    }

    private func fetchWasmAndReload(path: String) {
        let generation = bundleFetchGeneration
        Task {
            do {
                let wasmURL = baseURL
                    .appending(path: "bundle/artifact")
                    .appending(path: path)
                let data = try await tryFetchCompressed(url: wasmURL)
                guard generation == bundleFetchGeneration else { return }
                try runtime.hotReloadWasm(wasmBytes: data)
            } catch {
                print("Hot reload failed: \(error.localizedDescription)")
            }
        }
    }

    private func scheduleReconnect() {
        guard reconnectAttempts < maxReconnectAttempts else {
            errors = ["Development server disconnected. Return to the launcher to reconnect."]
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

    // ─── Incremental Tree Diffing / Patch Mutations (Feature 3) ───

    private func parsePatchValue(_ valueStr: String) -> Any {
        if valueStr == "null" || valueStr.isEmpty {
            return NSNull()
        }
        if valueStr.hasPrefix("\"") && valueStr.hasSuffix("\"") {
            return String(valueStr.dropFirst().dropLast())
        }
        if valueStr == "true" {
            return true
        }
        if valueStr == "false" {
            return false
        }
        if let intVal = Int(valueStr) {
            return intVal
        }
        if let doubleVal = Double(valueStr) {
            return doubleVal
        }
        if valueStr.hasPrefix("{") {
            if let data = valueStr.data(using: .utf8),
               let dict = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                return dict
            }
        }
        if valueStr.hasPrefix("[") {
            if let data = valueStr.data(using: .utf8),
               let arr = try? JSONSerialization.jsonObject(with: data, options: []) as? [Any] {
                return arr
            }
        }
        return valueStr
    }

    private func modifyNode(in root: inout [String: Any], path: [Int], modifier: (inout [String: Any]) -> Void) {
        if path.isEmpty {
            if var rootNode = root["root"] as? [String: Any] {
                modifier(&rootNode)
                root["root"] = rootNode
            }
            return
        }
        
        func walk(node: inout [String: Any], pathSlice: ArraySlice<Int>) {
            if pathSlice.isEmpty {
                modifier(&node)
                return
            }
            
            guard var children = node["children"] as? [[String: Any]] else { return }
            let idx = pathSlice.first!
            guard idx >= 0 && idx < children.count else { return }
            
            var targetChild = children[idx]
            walk(node: &targetChild, pathSlice: pathSlice.dropFirst())
            children[idx] = targetChild
            node["children"] = children
        }
        
        if var rootNode = root["root"] as? [String: Any] {
            walk(node: &rootNode, pathSlice: ArraySlice(path))
            root["root"] = rootNode
        }
    }

    private func applySinglePatch(to root: inout [String: Any], patch: [String: Any]) {
        guard let pathArray = patch["path"] as? [Any] else { return }
        let path = pathArray.compactMap { ($0 as? NSNumber)?.intValue ?? ($0 as? Int) }
        guard let op = patch["op"] as? String else { return }
        let prop = patch["property"] as? String ?? ""
        let valueStr = patch["value"] as? String ?? ""

        switch op {
        case "Replace":
            if path.isEmpty {
                if let data = valueStr.data(using: .utf8),
                   let newRoot = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                    root["root"] = newRoot
                }
            } else {
                let parentPath = Array(path.dropLast())
                guard let lastIdx = path.last else { return }
                modifyNode(in: &root, path: parentPath) { parent in
                    guard var children = parent["children"] as? [[String: Any]] else { return }
                    if lastIdx >= 0 && lastIdx < children.count {
                        if let data = valueStr.data(using: .utf8),
                           let newChild = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                            children[lastIdx] = newChild
                            parent["children"] = children
                        }
                    }
                }
            }
        case "UpdateProperty":
            if prop.isEmpty { return }
            modifyNode(in: &root, path: path) { target in
                let parsedValue = self.parsePatchValue(valueStr)
                if prop.hasPrefix("style.") {
                    let styleKey = String(prop.dropFirst(6))
                    var style = target["style"] as? [String: Any] ?? [:]
                    if parsedValue is NSNull {
                        style.removeValue(forKey: styleKey)
                    } else {
                        style[styleKey] = parsedValue
                    }
                    target["style"] = style
                } else if prop == "style" {
                    if parsedValue is NSNull {
                        target.removeValue(forKey: "style")
                    } else {
                        target["style"] = parsedValue
                    }
                } else {
                    if parsedValue is NSNull {
                        target.removeValue(forKey: prop)
                    } else {
                        target[prop] = parsedValue
                    }
                }
            }
        case "InsertChild":
            modifyNode(in: &root, path: path) { parent in
                var children = parent["children"] as? [[String: Any]] ?? []
                let index = Int(prop) ?? children.count
                if let data = valueStr.data(using: .utf8),
                   let childJson = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                    let targetIndex = max(0, min(index, children.count))
                    children.insert(childJson, at: targetIndex)
                    parent["children"] = children
                }
            }
        case "RemoveChild":
            modifyNode(in: &root, path: path) { parent in
                guard var children = parent["children"] as? [[String: Any]] else { return }
                if let index = Int(prop), index >= 0 && index < children.count {
                    children.remove(at: index)
                    parent["children"] = children
                }
            }
        default:
            break
        }
    }
}
