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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
                                DiagnosticsOverlay(errors = diagnosticErrors)
                                
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
            
            // 1. Try WASM logic
            runCatching {
                val wasmBytes = assets.open("feather/app.wasm").readBytes()
                runtime.loadWasm(wasmBytes.toList())
            }

            // 2. Try Binary IR (FlexBuffers)
            val binaryIr = runCatching { assets.open("feather/app.feather.fxb").readBytes() }.getOrNull()
            if (binaryIr != null) {
                val (root, errors) = parseFlexBuffersMessage(binaryIr, moduleRegistry)
                rootNode = root
                diagnosticErrors = errors
                return true
            }

            // 3. Try JSON IR fallback
            val jsonIr = runCatching { assets.open("feather/app.feather.json").bufferedReader().use { it.readText() } }.getOrNull()
            if (jsonIr != null) {
                val resolved = runtime.resolveStateTextNodes(jsonIr)
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

    private fun loadLocalBundle() {
        try {
            val hasLocalBundle = assets.list("feather")?.contains("app.feather.json") == true
            if (hasLocalBundle) {
                assets.open("feather/app.feather.json").bufferedReader().use { reader ->
                    val renderPayload = reader.readText()
                    val resolved = runtime.resolveStateTextNodes(renderPayload)
                    val (root, errors) = parseRenderMessage(resolved, moduleRegistry)
                    rootNode = root
                    diagnosticErrors = errors
                }
            }
        } catch (e: Exception) {
            diagnosticErrors = listOf("Failed to load local bundle: ${e.message}")
        }
    }

    private fun startCorePolling() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                if (runtime.pollRenderNeeded()) {
                    drainModuleCalls()
                    
                    if (runtime.hasWasm()) {
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
                    if (json?.optString("type") == "module_call") {
                        runtime.applyActionEvent(text)
                    } else if (json?.optString("type") == "state_update") {
                        val key = json.getString("key")
                        val value = json.get("value").toString()
                        runtime.setState(key, value)
                    } else if (json?.optString("type") == "hot-reload-wasm") {
                        val path = json.getString("path")
                        fetchWasmAndReload(path)
                    } else {
                        val resolved = runtime.resolveStateTextNodes(text)
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
                    runtime.hotReloadWasm(bytes.toList())
                }
            }
        }
    }

    private fun fetchBundleRender() {
        CoroutineScope(Dispatchers.IO).launch {
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

                // Fetch WASM if present (prefer compressed)
                val wasmArtifact = manifest.optJSONObject("wasm")
                if (wasmArtifact != null) {
                    val wasmPath = wasmArtifact.getString("path")
                    val wasmBytes = tryFetchCompressed("$devServerBaseUrl/bundle/artifact/$wasmPath")
                    if (wasmBytes != null) {
                        runtime.loadWasm(wasmBytes.toList())
                    }
                }

                val renderPath = manifest.getJSONObject("render").getString("path")
                val binaryPath = renderPath.replace(".json", ".fxb")
                
                // Try binary IR first
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
                            val (root, errors) = parseRenderMessage(resolved, moduleRegistry)
                            rootNode = root
                            diagnosticErrors = errors + missingModules
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
                            val (root, errors) = parseRenderMessage(resolved, moduleRegistry)
                            rootNode = root
                        }
                    }
                }
            }.onFailure { error ->
                CoroutineScope(Dispatchers.Main).launch {
                    rootNode = FeatherNode.TextNode("Bundle fetch failed: ${error.message}")
                    diagnosticErrors = listOf(error.message ?: "Unknown error")
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

        val fullEvent = event.put("type", "action")
        devSocket?.send(fullEvent.toString())
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
        moduleRegistry.register(NavigationModule(::sendAction))
        moduleRegistry.register(FilesModule())
        moduleRegistry.register(LocationModule())
        moduleRegistry.register(AuthModule())
        moduleRegistry.register(NotificationsModule())
        moduleRegistry.register(MapsModule())
        moduleRegistry.register(PaymentsModule())
        moduleRegistry.register(ReflectionModule())
        moduleRegistry.register(dev.feather.modules.camera.CameraModuleAndroid())
        moduleRegistry.register(dev.feather.modules.maps.MapsModuleAndroid())
    }

    private fun moduleContext(): FeatherModuleContext {
        return FeatherModuleContext(
            appContext = applicationContext,
            httpClient = client,
            sendStateAssignment = ::sendStateAssignment,
        )
    }

    private fun sendStateAssignment(key: String, value: String) {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        sendAction("$key=\"$escaped\"", null)
    }
}
