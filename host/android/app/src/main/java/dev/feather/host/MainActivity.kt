package dev.feather.host

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    
    private var devProjectPublicKey: String? = null
    private var isConnected by mutableStateOf(false)
    private var reconnectJob: Job? = null
    private var bundleJob: Job? = null
    private var reconnectAttempts = 0
    private var showProjectTools by mutableStateOf(false)
    @Volatile private var closing = false
    private var showLauncher by mutableStateOf(true)
    
    private var rootNode by mutableStateOf<FeatherNode>(
        FeatherNode.TextNode("Waiting for Feather CLI...")
    )
    private var diagnosticErrors by mutableStateOf<List<String>>(emptyList())
    
    private val client = OkHttpClient()
    private val moduleRegistry = ModuleRegistry()
    @Volatile private var devSocket: WebSocket? = null
    private val runtime = AppRuntime()
    private val bundleFetchMutex = Mutex()
    private val coreScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentJsonMessage: JSONObject? = null
    private var standaloneLinks: JSONArray? = null
    private var standaloneJavascript = false
    private val javascriptLock = Any()
    private var diagnosticSocket: WebSocket? = null
    private var diagnosticBundle: JSONObject? = null
    private val productionDiagnostics by lazy {
        packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            .metaData?.getBoolean("dev.feather.host.PRODUCTION_DIAGNOSTICS") == true
    }
    private val hostIdentity by lazy {
        val info = packageManager.getPackageInfo(packageName, 0)
        JSONObject()
            .put("id", packageName)
            .put("version", info.versionName)
            .put("build", androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toString())
    }

    private fun loadJavascript(manifest: String, source: String, key: String, socket: WebSocket? = null) {
        synchronized(javascriptLock) {
            forwardConsoleLogs()
            try {
                runtime.loadSignedJavascript(manifest, source, key)
            } catch (error: Exception) {
                reportDiagnostic("load", "error", error.message ?: "JavaScript load failed", socket, null)
                throw error
            }
            val value = JSONObject(manifest)
            val metadata = value.optJSONObject("metadata")
            diagnosticBundle = JSONObject()
                .put("app_id", metadata?.optString("app_id")?.takeIf { it.isNotBlank() && it != "null" } ?: value.optString("name"))
                .put("app_version", metadata?.optString("version_name")?.takeIf { it.isNotBlank() && it != "null" })
                .put("bundle_version", value.optInt("bundle_version"))
                .put("sha256", value.getJSONObject("javascript").getString("sha256"))
            diagnosticSocket = socket
        }
    }

    private fun renderJavascript(): String = synchronized(javascriptLock) {
        val document = try {
            runtime.renderJavascriptDocument()
        } catch (error: Exception) {
            runtime.drainDiagnostics().forEach { reportDiagnostic("runtime", it.level, it.message) }
            reportDiagnostic("render", "error", error.message ?: "JavaScript render failed")
            throw error
        }
        val diagnostics = JSONObject(document).optJSONArray("diagnostics")
        for (index in 0 until (diagnostics?.length() ?: 0)) {
            val event = diagnostics?.optJSONObject(index) ?: continue
            reportDiagnostic("runtime", event.optString("level"), event.optString("message"))
        }
        document
    }

    private fun reportDiagnostic(
        phase: String,
        level: String,
        message: String,
        socket: WebSocket? = diagnosticSocket,
        bundle: JSONObject? = diagnosticBundle
    ) {
        val forward = socket != null && socket === devSocket &&
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!forward && (socket != null || !productionDiagnostics)) return
        val event = JSONObject()
            .put("type", "diagnostic")
            .put("schema_version", 1)
            .put("platform", "android")
            .put("host", hostIdentity)
            .put("bundle", bundle)
            .put("phase", phase)
            .put("level", level)
            .put("message", message.take(16_384))
        if (forward) socket?.send(event.toString())
        else android.util.Log.e("FeatherDiagnostics", event.toString())
    }

    private fun forwardConsoleLogs() {
        val socket = devSocket
        val logs = runtime.drainConsoleLogs()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0 ||
            (diagnosticBundle != null && diagnosticSocket !== socket)) return
        logs.forEach { message ->
            socket?.send(JSONObject().put("type", "log").put("platform", "android").put("message", message).toString())
        }
    }

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
                    BackHandler(enabled = !showLauncher && !standaloneJavascript) {
                        showProjectTools = true
                    }
                    
                    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                        if (showLauncher && !hasLocalBundle) {
                            LauncherScreen(
                                initialIp = lastIp,
                                onConnect = { ip ->
                                    if (!updateUrls(ip)) {
                                        android.widget.Toast.makeText(this@MainActivity, diagnosticErrors.firstOrNull() ?: "Invalid project link", android.widget.Toast.LENGTH_LONG).show()
                                        return@LauncherScreen
                                    }
                                    prefs.edit().putString("last_ip", ip).apply()
                                    showLauncher = false
                                    resetAndConnect()
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.fillMaxSize().padding(top = if (standaloneJavascript) 0.dp else 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FeatherRenderer(
                                        node = rootNode,
                                        onAction = ::sendAction,
                                    )
                                }
                                DiagnosticsOverlay(
                                    errors = diagnosticErrors,
                                    onRetry = { resetAndConnect() }
                                )
                                
                                if (!standaloneJavascript) {
                                    IconButton(
                                        onClick = { showProjectTools = true },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = "Feather Go project tools")
                                    }
                                    if (showProjectTools) ProjectTools()
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Start Runtimes
        startCorePolling()
        
        handleProjectIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleProjectIntent(intent)
    }

    private fun handleProjectIntent(intent: android.content.Intent) {
        val data = intent.data ?: return
        if (intent.action != android.content.Intent.ACTION_VIEW) return
        if (data.scheme == "feather" && data.host == "app") {
            val path = appLinkPath(data.toString(), standaloneLinks) ?: return
            runtime.applyActionEvent(JSONObject().put("action", "__deepLink").put("payload", path).toString())
            return
        }
        if (!standaloneJavascript && data.scheme == "feather" && data.host == "open" && updateUrls(data.toString())) {
            showLauncher = false
            resetAndConnect()
        }
    }

    private fun updateUrls(input: String): Boolean {
        return try {
            val debugClient = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            val connection = ProjectConnection.parse(input, allowProjectKey = debugClient)
            devServerBaseUrl = connection.baseUrl
            devServerSocketUrl = connection.socketUrl
            devProjectPublicKey = connection.publicKey
            true
        } catch (error: Exception) {
            diagnosticErrors = listOf(error.message ?: "Invalid project link")
            false
        }
    }

    private fun resetAndConnect() {
        if (standaloneJavascript) return
        standaloneLinks = null
        reconnectJob?.cancel()
        reconnectAttempts = 0
        isConnected = false
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
                standaloneJavascript = true
                try {
                    val manifest = assets.open("feather/manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                    runtime.setState("host_standalone", "true")
                    loadJavascript(manifest, javascript, getString(R.string.feather_bundle_public_key).trim())
                    val document = renderJavascript()
                    currentJsonMessage = JSONObject(document)
                    val (root, errors) = parseRenderMessage(document, moduleRegistry)
                    rootNode = root
                    diagnosticErrors = errors
                    standaloneLinks = JSONObject(manifest).optJSONArray("deep_links")
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
        closing = true
        closeProjectConnection()
        client.connectionPool.evictAll()
        moduleRegistry.onDestroy()
        val engine = runtime
        coreScope.coroutineContext[Job]?.invokeOnCompletion { engine.destroy() }
        coreScope.cancel()
        super.onDestroy()
    }

    private fun closeProjectConnection() {
        reconnectJob?.cancel()
        bundleJob?.cancel()
        val socket = devSocket
        devSocket = null
        socket?.cancel()
        client.dispatcher.cancelAll()
        isConnected = false
    }

    @Composable
    private fun ProjectTools() {
        val modules = remember { runtime.getRegisteredModules().sortedBy { it.name } }
        val engine = when (runtimeName()) {
            "kotlin-js" -> "Kotlin/JS"
            "wasm" -> "WebAssembly"
            else -> "Layout"
        }
        AlertDialog(
            onDismissRequest = { showProjectTools = false },
            title = { Text("Project tools") },
            text = {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    Text(if (isConnected) "Connected · $engine" else "Disconnected · $engine")
                    modules.forEach { Text("${it.name}  ${it.version}") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showProjectTools = false
                    if (isConnected) fetchBundleRender() else resetAndConnect()
                }) { Text(if (isConnected) "Check for update" else "Reconnect") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProjectTools = false
                    closeProjectConnection()
                    showLauncher = true
                    setIntent(android.content.Intent(this, MainActivity::class.java))
                    recreate()
                }) { Text("Close project") }
            }
        )
    }

    private fun runtimeName() = when {
        runtime.hasJavascript() -> "kotlin-js"
        runtime.hasWasm() -> "wasm"
        else -> "layout"
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
                        runCatching { withContext(Dispatchers.Default) { renderJavascript() } }.onSuccess { document ->
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
                
                withContext(Dispatchers.Default) {
                    synchronized(javascriptLock) { forwardConsoleLogs() }
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
                coreScope.launch {
                    if (webSocket !== devSocket) return@launch
                    isConnected = true
                    if (reconnectAttempts > 0) {
                        reconnectAttempts = 0
                        fetchBundleRender()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                coreScope.launch {
                    if (webSocket !== devSocket) return@launch
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
                scheduleReconnect(webSocket)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect(webSocket)
            }
        })
    }

    private fun scheduleReconnect(socket: WebSocket) {
        coreScope.launch {
            if (socket !== devSocket || reconnectJob?.isActive == true) return@launch
            isConnected = false
            if (reconnectAttempts >= 5) {
                diagnosticErrors = listOf("Development server disconnected. Return to the launcher to reconnect.")
                return@launch
            }
            val retryDelay = 500L shl reconnectAttempts
            reconnectAttempts++
            diagnosticErrors = listOf("Development server disconnected. Reconnecting…")
            reconnectJob = coreScope.launch {
                delay(retryDelay)
                if (socket === devSocket) connectToDevServer()
            }
        }
    }

    private fun fetchWasmAndReload(path: String) {
        val socket = devSocket ?: return
        coreScope.launch(Dispatchers.IO) {
            runCatching {
                val wasmRequest = Request.Builder()
                    .url("$devServerBaseUrl/bundle/artifact/$path")
                    .build()
                client.newCall(wasmRequest).execute().use { response ->
                    response.body?.bytes()
                }
            }.onSuccess { bytes ->
                if (bytes != null && socket === devSocket) {
                    runtime.hotReloadWasm(bytes)
                }
            }
        }
    }

    private fun fetchBundleRender() {
        val socket = devSocket ?: return
        bundleJob?.cancel()
        bundleJob = coreScope.launch(Dispatchers.IO) {
            bundleFetchMutex.withLock {
            if (socket !== devSocket) return@withLock
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
                val hostPublicKeyB64 = devProjectPublicKey ?: getString(R.string.feather_bundle_public_key).trim()
                
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
                    if (socket !== devSocket) return@runCatching
                    loadJavascript(manifestPayload, source, hostPublicKeyB64, socket)
                    val document = renderJavascript()
                    coreScope.launch {
                        if (socket !== devSocket) return@launch
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
                    coreScope.launch {
                        rootNode = root
                        diagnosticErrors = errors + missingModules
                    }
                } else {
                    // Try FlexBuffers binary IR second
                    val fbBytes = tryFetchCompressed("$devServerBaseUrl/bundle/artifact/$binaryPath")
                    if (fbBytes != null) {
                        val (root, errors) = parseFlexBuffersMessage(fbBytes, moduleRegistry)
                        coreScope.launch {
                            rootNode = root
                            diagnosticErrors = errors + missingModules
                        }
                    } else {
                        // Fallback to JSON
                        renderPayload = tryFetchCompressedString("$devServerBaseUrl/bundle/artifact/$renderPath")
                        if (renderPayload != null) {
                            coreScope.launch {
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
                    coreScope.launch {
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
                    if (socket !== devSocket) return@launch
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
        val compressed = runCatching {
            client.newCall(gzRequest).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.byteStream()?.let { stream ->
                    java.util.zip.GZIPInputStream(stream).use { it.readBytes() }
                }
            }
        }.getOrNull()
        if (compressed != null) {
            return compressed
        }
        
        val request = Request.Builder().url(url).build()
        return runCatching {
            client.newCall(request).execute().use { if (it.isSuccessful) it.body?.bytes() else null }
        }.getOrNull()
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
        if (moduleName == "feather-device" && method == "hasModule") {
            val available = runtime.getRegisteredModules().any { it.name == payload.optString("moduleName") }
            sendStateAssignment(resultStateKey, available.toString())
            return
        }
        if (moduleName == "feather-device" && method == "getRuntimeInfo") {
            val modules = JSONObject()
            runtime.getRegisteredModules().forEach { modules.put(it.name, it.version) }
            sendStateAssignment(resultStateKey, JSONObject().put("runtime", runtimeName()).put("modules", modules).toString())
            return
        }
        
        val baseContext = moduleContext()
        val context = if (resultStateKey.isNotEmpty()) {
            baseContext.copy(sendStateAssignment = { _, value ->
                baseContext.sendStateAssignment(resultStateKey, value)
            })
        } else {
            baseContext
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
        val host = java.lang.ref.WeakReference(this)
        moduleRegistry.register(NavigationModule { action, payload ->
            host.get()?.let { activity ->
                activity.coreScope.launch {
                    if (!activity.closing) activity.sendAction(action, payload)
                }
            }
        })
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
        val host = java.lang.ref.WeakReference(this)
        return FeatherModuleContext(
            appContext = applicationContext,
            httpClient = client,
            sendStateAssignment = { key, value ->
                host.get()?.let { activity ->
                    activity.coreScope.launch {
                        if (!activity.closing) activity.sendStateAssignment(key, value)
                    }
                }
            },
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
