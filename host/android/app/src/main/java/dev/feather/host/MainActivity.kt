package dev.feather.host

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import uniffi.feather_core.AppRuntime

class MainActivity : ComponentActivity() {

    private var devServerBaseUrl by mutableStateOf("http://10.0.2.2:8080")
    private var devServerSocketUrl by mutableStateOf("ws://10.0.2.2:8080/ws")
    
    private var isConnected by mutableStateOf(false)
    private var showLauncher by mutableStateOf(true)
    
    private var rootNode by mutableStateOf<FeatherNode>(
        FeatherNode.TextNode("Waiting for Feather CLI...")
    )
    private var diagnosticErrors by mutableStateOf<List<String>>(emptyList())
    
    private val client = OkHttpClient()
    private val moduleRegistry = ModuleRegistry()
    private var devSocket: WebSocket? = null
    private val runtime = AppRuntime()
    private val bundleFetchMutex = Mutex()
    private val coreScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentJsonMessage: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load last used IP
        val prefs = getSharedPreferences("feather_internal", Context.MODE_PRIVATE)
        val lastIp = prefs.getString("last_ip", "10.0.2.2") ?: "10.0.2.2"
        updateUrls(lastIp)

        // 1. Register Modules
        registerOfficialModules()
        moduleRegistry.registeredModules().forEach { module ->
            runtime.registerModule(module.name, NativeModuleWrapper(module, moduleContext()))
        }
        moduleRegistry.initializeAll(moduleContext())

        // ─── Phase 1.5: Standalone Production Loading ───
        val hasLocalBundle = loadStandaloneBundle()

        // 2. Set UI
        setContent {
            CompositionLocalProvider(LocalModuleRegistry provides moduleRegistry) {
                MaterialTheme {
                    BackHandler(enabled = !showLauncher) {
                        if (!showLauncher) {
                            showLauncher = true
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showLauncher && !hasLocalBundle) {
                            LauncherScreen(
                                initialIp = lastIp,
                                onConnect = { ip ->
                                    updateUrls(ip)
                                    prefs.edit().putString("last_ip", ip).apply()
                                    showLauncher = false
                                    resetAndConnect()
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                FeatherRenderer(
                                    node = rootNode,
                                    onAction = ::sendAction,
                                )
                                DiagnosticsOverlay(
                                    errors = diagnosticErrors,
                                    onRetry = { resetAndConnect() }
                                )
                                
                                // Floating FAB to go back to launcher
                                FloatingActionButton(
                                    onClick = { showLauncher = true },
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                    backgroundColor = Color(0xFFE50914)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Start Runtimes
        startCorePolling()
        
        // Handle Intent Data
        val data = intent.data
        val deepLinkUrl = if (data?.scheme == "feather" && data.host == "open") {
            data.getQueryParameter("url")
        } else {
            null
        }

        if (deepLinkUrl != null) {
            val ip = deepLinkUrl.removePrefix("http://").removePrefix("https://").substringBefore(":")
            updateUrls(ip)
            showLauncher = false
            resetAndConnect()
        }
    }

    private fun updateUrls(ip: String) {
        val cleanInput = ip.trim()
        try {
            if (cleanInput.startsWith("http://") || cleanInput.startsWith("https://")) {
                val url = java.net.URL(cleanInput)
                val protocol = if (url.protocol == "https") "wss" else "ws"
                val port = if (url.port != -1) ":${url.port}" else ""
                devServerBaseUrl = "${url.protocol}://${url.host}$port"
                devServerSocketUrl = "$protocol://${url.host}$port/ws"
            } else {
                devServerBaseUrl = "http://$cleanInput:8080"
                devServerSocketUrl = "ws://$cleanInput:8080/ws"
            }
        } catch (e: Exception) {
            // Fallback for malformed URLs
            devServerBaseUrl = "http://$cleanInput:8080"
            devServerSocketUrl = "ws://$cleanInput:8080/ws"
        }
    }

    private fun resetAndConnect() {
        diagnosticErrors = emptyList()
        rootNode = FeatherNode.TextNode("Connecting to $devServerBaseUrl...")
        devSocket?.close(1000, "Reconnecting")
        connectToDevServer()
        fetchBundleRender()
    }

    private fun loadStandaloneBundle(): Boolean {
        return try {
            val assets = applicationContext.assets
            
            // A packaged JavaScript app must authenticate before any code executes.
            val javascript = runCatching { assets.open("feather/app.js").bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
            if (javascript != null) {
                try {
                    val manifest = assets.open("feather/manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                    runtime.loadSignedJavascript(manifest, javascript, getString(R.string.feather_bundle_public_key).trim())
                    val document = runtime.renderJavascriptDocument()
                    currentJsonMessage = JSONObject(document)
                    val (root, errors) = parseRenderMessage(document, moduleRegistry)
                    rootNode = root
                    diagnosticErrors = errors
                } catch (error: Exception) {
                    rootNode = FeatherNode.TextNode("Bundle verification or startup failed")
                    diagnosticErrors = listOf(error.message ?: "JavaScript bundle rejected")
                }
                // Never fall back to an unsigned layout after rejecting signed code.
                return true
            }

            // 1. Try WASM logic
            runCatching {
                val wasmBytes = assets.open("feather/app.wasm").readBytes()
                runtime.loadWasm(wasmBytes)
            }

            // 2. Try FlatBuffers (zero-copy) first
            val flatIr = runCatching { assets.open("feather/app.feather.fb").readBytes() }.getOrNull()
            if (flatIr != null) {
                val (root, errors) = parseFlatBuffersMessage(flatIr, moduleRegistry)
                rootNode = root
                diagnosticErrors = errors
                return true
            }

            // 3. Try Binary IR (FlexBuffers)
            val binaryIr = runCatching { assets.open("feather/app.feather.fxb").readBytes() }.getOrNull()
            if (binaryIr != null) {
                val (root, errors) = parseFlexBuffersMessage(binaryIr, moduleRegistry)
                rootNode = root
                diagnosticErrors = errors
                return true
            }

            // 4. Try JSON IR fallback
            val jsonIr = runCatching { assets.open("feather/app.feather.json").bufferedReader().use { it.readText() } }.getOrNull()
            if (jsonIr != null) {
                val resolved = runtime.resolveStateTextNodes(jsonIr)
                currentJsonMessage = JSONObject(resolved)
                val (root, errors) = parseRenderMessage(resolved, moduleRegistry)
                rootNode = root
                diagnosticErrors = errors
                return true
            }

            false
        } catch (e: Exception) {
            false
        }
    }

    override fun onStart() {
        super.onStart()
        moduleRegistry.onStart()
    }

    override fun onResume() {
        super.onResume()
        moduleRegistry.onResume()
        runtime.pushNativeEvent("onResume", "{}")
    }

    override fun onPause() {
        super.onPause()
        moduleRegistry.onPause()
        runtime.pushNativeEvent("onPause", "{}")
    }

    override fun onStop() {
        super.onStop()
        moduleRegistry.onStop()
        runtime.pushNativeEvent("onStop", "{}")
    }

    override fun onDestroy() {
        coreScope.cancel()
        devSocket?.close(1000, "Activity destroyed")
        super.onDestroy()
        moduleRegistry.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        moduleRegistry.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        moduleRegistry.onActivityResult(requestCode, resultCode, data)
    }

    private fun startCorePolling() {
        coreScope.launch {
            while (true) {
                if (runtime.pollRenderNeeded()) {
                    drainModuleCalls()
                    
                    if (runtime.hasJavascript()) {
                        runCatching { withContext(Dispatchers.Default) { runtime.renderJavascriptDocument() } }.onSuccess { document ->
                            currentJsonMessage = JSONObject(document)
                            val (root, errors) = parseRenderMessage(document, moduleRegistry)
                            rootNode = root
                            diagnosticErrors = errors
                        }.onFailure { error -> diagnosticErrors = listOf(error.message ?: "Kotlin runtime error") }
                    } else if (runtime.hasWasm()) {
                        val renderTree = runtime.renderWasm()
                        if (renderTree != null) {
                            if (renderTree.startsWith("base64:")) {
                                val bytes = android.util.Base64.decode(renderTree.substring(7), android.util.Base64.DEFAULT)
                                // We use MessagePack for SDK dynamic IR
                                val (root, errors) = parseMessagePackMessage(bytes, moduleRegistry)
                                rootNode = root
                                diagnosticErrors = errors
                            } else {
                                val resolved = runtime.resolveStateTextNodes(renderTree)
                                currentJsonMessage = JSONObject(resolved)
                                val (root, errors) = parseRenderMessage(resolved, moduleRegistry)
                                rootNode = root
                            }
                        }
                    }
                }
                
                // Drain and send logs to CLI
                val logs = runtime.drainConsoleLogs()
                logs.forEach { log ->
                    val logEvent = JSONObject()
                        .put("type", "log")
                        .put("message", log)
                    devSocket?.send(logEvent.toString())
                }

                kotlinx.coroutines.delay(16)
            }
        }
    }

    private fun drainModuleCalls() {
        val calls = runtime.drainModuleCalls()
        calls.forEach { callJson ->
            val json = JSONObject(callJson)
            handleStructuredModuleCall(json)
        }
    }

    private fun connectToDevServer() {
        val request = Request.Builder().url(devServerSocketUrl).build()
        devSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                CoroutineScope(Dispatchers.Main).launch {
                    isConnected = true
                    rootNode = FeatherNode.TextNode("Connected to Dev Server!")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    val type = json?.optString("type")
                    if (type == "hot-reload-js") {
                        fetchBundleRender()
                    } else if (type == "devtools_state_update") {
                        runtime.setState(json!!.getString("key"), json.get("value").toString())
                    } else if (runtime.hasJavascript() && type in listOf("render", "patch", "state_update", "module_call")) {
                        // The device owns its app state; server renders are previews.
                    } else if (runtime.hasJavascript() && type == "render_error") {
                        val diagnostics = json?.optJSONArray("diagnostics")
                        diagnosticErrors = (0 until (diagnostics?.length() ?: 0)).map { diagnostics!!.getJSONObject(it).optString("message") }
                    } else if (type == "module_call") {
                        runtime.applyActionEvent(text)
                    } else if (json?.optString("type") == "state_update") {
                        val key = json.getString("key")
                        val value = json.get("value").toString()
                        runtime.setState(key, value)
                    } else if (json?.optString("type") == "hot-reload-wasm") {
                        val path = json.getString("path")
                        fetchWasmAndReload(path)
                    } else if (json?.optString("type") == "patch") {
                        val patchesArray = json.getJSONArray("patches")
                        val activeJson = currentJsonMessage
                        if (activeJson != null) {
                            applyPatches(activeJson, patchesArray)
                            val resolved = runtime.resolveStateTextNodes(activeJson.toString())
                            val (root, errors) = parseRenderMessage(resolved, moduleRegistry)
                            rootNode = root
                            diagnosticErrors = errors
                        }
                    } else {
                        val resolved = runtime.resolveStateTextNodes(text)
                        currentJsonMessage = JSONObject(resolved)
                        val (root, errors) = parseRenderMessage(resolved, moduleRegistry)
                        rootNode = root
                        diagnosticErrors = errors
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                CoroutineScope(Dispatchers.Main).launch {
                    isConnected = false
                    rootNode = FeatherNode.TextNode("Connection Failed: ${t.message}\nCheck if CLI is running at $devServerSocketUrl")
                }
            }
        })
    }

    private fun fetchWasmAndReload(path: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val wasmRequest = Request.Builder()
                    .url("$devServerBaseUrl/bundle/artifact/$path")
                    .build()
                client.newCall(wasmRequest).execute().use { response ->
                    response.body?.bytes()
                }
            }.onSuccess { bytes ->
                if (bytes != null) {
                    runtime.hotReloadWasm(bytes)
                }
            }
        }
    }

    private fun fetchBundleRender() {
        coreScope.launch(Dispatchers.IO) {
            bundleFetchMutex.withLock {
            var missingModules: List<String> = emptyList()
            var renderPayload: String? = null
            
            runCatching {
                val manifestRequest = Request.Builder()
                    .url("$devServerBaseUrl/bundle/manifest")
                    .build()
                val manifestPayload = client.newCall(manifestRequest).execute().use { response ->
                    response.body?.string().orEmpty()
                }
                val manifest = JSONObject(manifestPayload)

                // ─── Feature 1: Cryptographic Bundle Verification & Code Signing ───
                val signature = manifest.optString("signature", "")
                val hostPublicKeyB64 = getString(R.string.feather_bundle_public_key).trim()
                
                if (signature.isEmpty() || !uniffi.feather_core.verifyManifestSignature(manifestPayload, signature, hostPublicKeyB64)) {
                    throw SecurityException("CRYPTOGRAPHIC SECURE BOOT FAULT")
                }
                
                val modulesArray = manifest.optJSONArray("modules")
                val requiredModules = mutableListOf<uniffi.feather_core.BundleModule>()
                if (modulesArray != null) {
                    for (i in 0 until modulesArray.length()) {
                        val obj = modulesArray.optJSONObject(i) ?: continue
                        requiredModules.add(uniffi.feather_core.BundleModule(
                            obj.getString("name"),
                            obj.optString("moduleVersion", "1.0.0"),
                            mutableListOf<String>().apply {
                                val platforms = obj.optJSONArray("modulePlatforms")
                                if (platforms != null) for (j in 0 until platforms.length()) add(platforms.getString(j))
                            },
                            mutableListOf<String>().apply {
                                val permissions = obj.optJSONArray("modulePermissions")
                                if (permissions != null) for (j in 0 until permissions.length()) add(permissions.getString(j))
                            }
                        ))
                    }
                }
                missingModules = runtime.checkRequirements(requiredModules, "android")

                if (manifest.optString("runtime") == "kotlin-js") {
                    val artifact = manifest.getJSONObject("javascript")
                    require(artifact.getString("path") == "app.js") { "Invalid JavaScript artifact path" }
                    val request = Request.Builder().url("$devServerBaseUrl/bundle/artifact/app.js").build()
                    val source = client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "JavaScript download failed: ${response.code}" }
                        response.body?.string() ?: error("Empty JavaScript response")
                    }
                    runtime.loadSignedJavascript(manifestPayload, source, hostPublicKeyB64)
                    val document = runtime.renderJavascriptDocument()
                    coreScope.launch {
                        currentJsonMessage = JSONObject(document)
                        val (root, errors) = parseRenderMessage(document, moduleRegistry)
                        rootNode = root
                        diagnosticErrors = errors + missingModules
                    }
                    return@runCatching
                }

                // Fetch WASM if present (prefer compressed)
                val wasmArtifact = manifest.optJSONObject("wasm")
                if (wasmArtifact != null) {
                    val wasmPath = wasmArtifact.getString("path")
                    val wasmBytes = tryFetchCompressed("$devServerBaseUrl/bundle/artifact/$wasmPath")
                    if (wasmBytes != null) {
                        runtime.loadWasm(wasmBytes)
                    }
                }

                val renderPath = manifest.getJSONObject("render").getString("path")
                val binaryPath = renderPath.replace(".json", ".fxb")
                val flatPath = renderPath.replace(".json", ".fb")
                
                // Try FlatBuffers first
                val flatBytes = tryFetchCompressed("$devServerBaseUrl/bundle/artifact/$flatPath")
                if (flatBytes != null) {
                    val (root, errors) = parseFlatBuffersMessage(flatBytes, moduleRegistry)
                    CoroutineScope(Dispatchers.Main).launch {
                        rootNode = root
                        diagnosticErrors = errors + missingModules
                    }
                } else {
                    // Try FlexBuffers binary IR second
                    val fbBytes = tryFetchCompressed("$devServerBaseUrl/bundle/artifact/$binaryPath")
                    if (fbBytes != null) {
                        val (root, errors) = parseFlexBuffersMessage(fbBytes, moduleRegistry)
                        CoroutineScope(Dispatchers.Main).launch {
                            rootNode = root
                            diagnosticErrors = errors + missingModules
                        }
                    } else {
                        // Fallback to JSON
                        renderPayload = tryFetchCompressedString("$devServerBaseUrl/bundle/artifact/$renderPath")
                        if (renderPayload != null) {
                            CoroutineScope(Dispatchers.Main).launch {
                                val resolved = runtime.resolveStateTextNodes(renderPayload!!)
                                currentJsonMessage = JSONObject(resolved)
                                val (root, errors) = parseRenderMessage(resolved, moduleRegistry)
                                rootNode = root
                                diagnosticErrors = errors + missingModules
                            }
                        }
                    }
                }
            }.onSuccess {
                // If we didn't have binary and didn't have WASM override, renderPayload was used above.
                // If we HAVE WASM, we still want to use runtime.renderWasm() which returns JSON for now.
                if (runtime.hasWasm()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val renderTree = runtime.renderWasm()
                        if (renderTree != null) {
                            val resolved = runtime.resolveStateTextNodes(renderTree)
                            currentJsonMessage = JSONObject(resolved)
                            val (root, errors) = parseRenderMessage(resolved, moduleRegistry)
                            rootNode = root
                        }
                    }
                }
            }.onFailure { error ->
                coreScope.launch {
                    if (runtime.hasJavascript()) {
                        diagnosticErrors = listOf(error.message ?: "JavaScript update rejected")
                        return@launch
                    }
                    if (error is SecurityException && error.message == "CRYPTOGRAPHIC SECURE BOOT FAULT") {
                        rootNode = FeatherNode.TextNode("CRYPTOGRAPHIC SECURE BOOT FAULT")
                        diagnosticErrors = listOf(
                            "CRITICAL SECURITY ALERT: CRYPTOGRAPHIC SECURE BOOT FAULT",
                            "The dynamic bundle signature could not be verified against the host public key.",
                            "Execution halted to prevent unauthorized remote code execution."
                        )
                    } else {
                        rootNode = FeatherNode.TextNode("Bundle fetch failed: ${error.message}")
                        diagnosticErrors = listOf(error.message ?: "Unknown error")
                    }
                }
            }
            }
        }
    }

    private fun tryFetchCompressed(url: String): ByteArray? {
        val gzUrl = "$url.gz"
        val gzRequest = Request.Builder().url(gzUrl).build()
        val gzResponse = try { client.newCall(gzRequest).execute() } catch (e: Exception) { null }
        
        if (gzResponse != null && gzResponse.isSuccessful) {
            return gzResponse.body?.byteStream()?.let { 
                java.util.zip.GZIPInputStream(it).readBytes() 
            }
        }
        
        val request = Request.Builder().url(url).build()
        return try { client.newCall(request).execute().body?.bytes() } catch (e: Exception) { null }
    }

    private fun tryFetchCompressedString(url: String): String? {
        return tryFetchCompressed(url)?.toString(Charsets.UTF_8)
    }

    private fun sendAction(action: String, payload: String?) {
        val event = JSONObject()
            .put("action", action)
            .put("payload", if (payload != null) {
                runCatching { JSONObject(payload) }.getOrDefault(payload)
            } else JSONObject.NULL)
        
        runtime.applyActionEvent(event.toString())

        if (action.startsWith("host:")) {
            handleHostAction(action)
            return
        }

        if (runtime.hasJavascript()) {
            devSocket?.send(JSONObject().put("type", "runtime_state").put("state", JSONObject(runtime.getAllState())).toString())
        } else {
            val fullEvent = event.put("type", "action")
            devSocket?.send(fullEvent.toString())
        }
    }

    private fun handleHostAction(action: String) {
        val command = action.removePrefix("host:")
        if (moduleRegistry.handleLegacyCommand(command, moduleContext())) {
            return
        }
        diagnosticErrors = diagnosticErrors + "Unhandled host command: $command"
    }

    private fun handleStructuredModuleCall(json: JSONObject) {
        val moduleName = json.getString("module")
        val method = json.getString("method")
        val payload = json.optJSONObject("payload") ?: JSONObject()
        val resultStateKey = json.optString("resultStateKey")
        
        val context = if (resultStateKey.isNotEmpty()) {
            moduleContext().copy(sendStateAssignment = { _, value ->
                sendStateAssignment(resultStateKey, value)
            })
        } else {
            moduleContext()
        }
        
        if (!moduleRegistry.call(moduleName, method, payload, context)) {
            diagnosticErrors = diagnosticErrors + "Module call failed or not found: $moduleName/$method"
        }
    }

    private fun registerOfficialModules() {
        moduleRegistry.register(NetworkModule())
        moduleRegistry.register(StorageModule())
        moduleRegistry.register(MediaModule())
        moduleRegistry.register(WebViewModule())
        moduleRegistry.register(DeviceModule())
        moduleRegistry.register(DeviceInfoModule())
        moduleRegistry.register(NavigationModule(::sendAction))
        moduleRegistry.register(FilesModule())
        // Stub modules - disabled for v1 release (documented as coming soon in ROADMAP)
        // moduleRegistry.register(LocationModule())
        // moduleRegistry.register(AuthModule())
        // moduleRegistry.register(NotificationsModule())
        // moduleRegistry.register(MapsModule())
        // moduleRegistry.register(PaymentsModule())
        moduleRegistry.register(ReflectionModule())
        // Sample camera/maps plugins are opt-in native dependencies, discovered
        // through PluginLoader when included in a host build.

        // Load and register third-party plugins dynamically
        try {
            val plugins = PluginLoader.loadPlugins(applicationContext)
            plugins.forEach { plugin ->
                moduleRegistry.register(plugin)
                android.util.Log.i("FeatherPlugin", "Registered dynamic plugin: ${plugin.name}")
            }
        } catch (e: Exception) {
            android.util.Log.e("FeatherPlugin", "Error loading dynamic plugins", e)
        }
    }

    private fun moduleContext(): FeatherModuleContext {
        return FeatherModuleContext(
            appContext = applicationContext,
            httpClient = client,
            sendStateAssignment = ::sendStateAssignment,
        )
    }

    private fun sendStateAssignment(key: String, value: String) {
        if (key.isEmpty()) return
        runtime.setState(key, value)
        val typed = runCatching { org.json.JSONTokener(value).nextValue() }.getOrElse { value }
        devSocket?.send(JSONObject().put("type", "state_update").put("key", key).put("value", typed).toString())
    }

    // ─── Incremental Tree Diffing / Patch Mutations (Feature 3) ───

    private data class NodeLocation(val parent: JSONObject?, val target: JSONObject, val index: Int)

    private fun findNodeByPath(root: JSONObject, path: List<Int>): JSONObject? {
        var current: JSONObject = root.optJSONObject("root") ?: return null
        for (index in path) {
            val children = current.optJSONArray("children") ?: return null
            if (index < 0 || index >= children.length()) {
                return null
            }
            current = children.optJSONObject(index) ?: return null
        }
        return current
    }

    private fun findNodeLocation(root: JSONObject, path: List<Int>): NodeLocation? {
        val rootNode = root.optJSONObject("root") ?: return null
        if (path.isEmpty()) {
            return NodeLocation(null, rootNode, -1)
        }
        var current = rootNode
        for (i in 0 until path.size - 1) {
            val idx = path[i]
            val children = current.optJSONArray("children") ?: return null
            if (idx < 0 || idx >= children.length()) return null
            current = children.optJSONObject(idx) ?: return null
        }
        val lastIdx = path.last()
        val children = current.optJSONArray("children") ?: return null
        if (lastIdx < 0 || lastIdx >= children.length()) return null
        val target = children.optJSONObject(lastIdx) ?: return null
        return NodeLocation(current, target, lastIdx)
    }

    private fun parsePatchValue(valueStr: String): Any {
        if (valueStr == "null" || valueStr.isEmpty()) return JSONObject.NULL
        if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
            return valueStr.substring(1, valueStr.length - 1)
        }
        if (valueStr == "true") return true
        if (valueStr == "false") return false
        val intVal = valueStr.toIntOrNull()
        if (intVal != null) return intVal
        val doubleVal = valueStr.toDoubleOrNull()
        if (doubleVal != null) return doubleVal
        
        if (valueStr.startsWith("{")) {
            try { return JSONObject(valueStr) } catch(e: Exception) {}
        }
        if (valueStr.startsWith("[")) {
            try { return JSONArray(valueStr) } catch(e: Exception) {}
        }
        return valueStr
    }

    private fun insertIntoJsonArray(array: JSONArray, index: Int, value: Any) {
        val temp = JSONArray()
        val targetIndex = index.coerceIn(0, array.length())
        for (i in 0 until targetIndex) {
            temp.put(array.get(i))
        }
        temp.put(value)
        for (i in targetIndex until array.length()) {
            temp.put(array.get(i))
        }
        while (array.length() > 0) {
            array.remove(0)
        }
        for (i in 0 until temp.length()) {
            array.put(temp.get(i))
        }
    }

    private fun applyPatches(root: JSONObject, patches: JSONArray) {
        for (i in 0 until patches.length()) {
            val patch = patches.getJSONObject(i)
            val pathArray = patch.getJSONArray("path")
            val path = List(pathArray.length()) { pathArray.getInt(it) }
            val op = patch.getString("op")
            val prop = patch.optString("property", "")
            val valueStr = patch.optString("value", "")

            when (op) {
                "Replace" -> {
                    if (path.isEmpty()) {
                        val newRoot = JSONObject(valueStr)
                        root.put("root", newRoot)
                    } else {
                        val location = findNodeLocation(root, path)
                        if (location != null && location.parent != null) {
                            val children = location.parent.getJSONArray("children")
                            children.put(location.index, JSONObject(valueStr))
                        }
                    }
                }
                "UpdateProperty" -> {
                    val target = findNodeByPath(root, path)
                    if (target != null && prop.isNotEmpty()) {
                        val parsedValue = parsePatchValue(valueStr)
                        if (prop.startsWith("style.")) {
                            val styleKey = prop.substring(6)
                            val style = target.optJSONObject("style") ?: JSONObject().also { target.put("style", it) }
                            if (parsedValue == JSONObject.NULL) {
                                style.remove(styleKey)
                            } else {
                                style.put(styleKey, parsedValue)
                            }
                        } else if (prop == "style") {
                            if (parsedValue == JSONObject.NULL) {
                                target.remove("style")
                            } else {
                                target.put("style", parsedValue)
                            }
                        } else {
                            if (parsedValue == JSONObject.NULL) {
                                target.remove(prop)
                            } else {
                                target.put(prop, parsedValue)
                            }
                        }
                    }
                }
                "InsertChild" -> {
                    val parent = findNodeByPath(root, path)
                    if (parent != null) {
                        val children = parent.optJSONArray("children") ?: JSONArray().also { parent.put("children", it) }
                        val index = prop.toIntOrNull() ?: children.length()
                        val childJson = JSONObject(valueStr)
                        insertIntoJsonArray(children, index, childJson)
                    }
                }
                "RemoveChild" -> {
                    val parent = findNodeByPath(root, path)
                    if (parent != null) {
                        val children = parent.optJSONArray("children")
                        val index = prop.toIntOrNull()
                        if (children != null && index != null && index >= 0 && index < children.length()) {
                            children.remove(index)
                        }
                    }
                }
            }
        }
    }
}
