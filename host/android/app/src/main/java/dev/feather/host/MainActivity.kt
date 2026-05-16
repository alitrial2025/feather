package dev.feather.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Switch
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

val LocalModuleRegistry = staticCompositionLocalOf<ModuleRegistry?> { null }

// MARK: - Style Data

data class FeatherStyle(
    val color: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val textAlign: String? = null,
    val textDecoration: String? = null,
    val fontFamily: String? = null,
    val background: String? = null,
    val padding: Int? = null,
    val paddingHorizontal: Int? = null,
    val paddingVertical: Int? = null,
    val paddingTop: Int? = null,
    val paddingBottom: Int? = null,
    val paddingStart: Int? = null,
    val paddingEnd: Int? = null,
    val margin: Int? = null,
    val cornerRadius: Int? = null,
    val borderColor: String? = null,
    val borderWidth: Int? = null,
    val width: String? = null,
    val height: String? = null,
    val minWidth: String? = null,
    val minHeight: String? = null,
    val maxWidth: String? = null,
    val maxHeight: String? = null,
    val alignment: String? = null,
    val spacing: Int? = null,
    val opacity: Float? = null,
    val textColor: String? = null,
    val placeholderColor: String? = null,
    val contentMode: String? = null,
    val shadow: String? = null,
    val elevation: Int? = null,
    val maxLines: Int? = null,
    val flex: Int? = null,
    val fillMaxWidth: Boolean? = null,
    val fillMaxHeight: Boolean? = null,
    val aspectRatio: Float? = null,
    val gradient: String? = null,
    val clip: Boolean? = null,
    val keyboardType: String? = null,
    val maxLength: Int? = null,
    val multiline: Boolean? = null,
    val secure: Boolean? = null,
    val direction: String? = null,
    val position: String? = null,
    val top: Float? = null,
    val bottom: Float? = null,
    val left: Float? = null,
    val right: Float? = null,
    val zIndex: Float? = null,
)

// MARK: - Node Tree

sealed interface FeatherNode {
    // Basic
    data class TextNode(val text: String, val style: FeatherStyle? = null) : FeatherNode
    data class ButtonNode(val text: String, val action: String, val enabled: Boolean = true, val style: FeatherStyle? = null) : FeatherNode
    data class TextInputNode(val value: String, val placeholder: String, val action: String, val label: String? = null, val style: FeatherStyle? = null) : FeatherNode
    data class ImageNode(val src: String, val alt: String, val style: FeatherStyle? = null) : FeatherNode
    data class IconNode(val name: String, val size: Int = 24, val color: String? = null, val action: String? = null) : FeatherNode
    data class IconButtonNode(val icon: String, val action: String, val size: Int = 24, val color: String? = null, val background: String? = null) : FeatherNode
    data class SpacerNode(val flex: Int = 1) : FeatherNode
    data class DividerNode(val color: String? = null, val thickness: Int = 1, val indent: Int = 0) : FeatherNode

    // Layouts
    data class ColumnNode(val children: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class RowNode(val children: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class BoxNode(val children: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class LazyColumnNode(val items: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class LazyRowNode(val items: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class LazyGridNode(val items: List<FeatherNode>, val columns: Int = 2, val style: FeatherStyle? = null) : FeatherNode
    data class ScrollViewNode(val children: List<FeatherNode>, val direction: String = "vertical", val style: FeatherStyle? = null) : FeatherNode
    data class CardNode(val children: List<FeatherNode>, val action: String? = null, val style: FeatherStyle? = null) : FeatherNode
    data class SurfaceNode(val children: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class AspectRatioNode(val ratio: Float, val child: FeatherNode) : FeatherNode

    // Interactive
    data class SwitchNode(val checked: Boolean, val action: String, val enabled: Boolean = true, val label: String? = null, val trackColor: String? = null, val thumbColor: String? = null) : FeatherNode
    data class CheckboxNode(val checked: Boolean, val action: String, val enabled: Boolean = true, val label: String? = null, val color: String? = null) : FeatherNode
    data class RadioGroupNode(val selected: String, val action: String, val options: List<RadioOptionData>) : FeatherNode
    data class SliderNode(val value: Float, val min: Float, val max: Float, val step: Float?, val action: String, val label: String? = null, val trackColor: String? = null, val thumbColor: String? = null) : FeatherNode
    data class SearchBarNode(val value: String, val placeholder: String, val action: String, val style: FeatherStyle? = null) : FeatherNode
    data class DropdownMenuNode(val selected: String, val options: List<String>, val action: String, val label: String? = null, val style: FeatherStyle? = null) : FeatherNode

    // Progress
    data class LinearProgressNode(val progress: Float?, val color: String?, val trackColor: String?) : FeatherNode
    data class CircularProgressNode(val progress: Float?, val color: String?, val size: Int = 40, val strokeWidth: Int = 4) : FeatherNode

    // Feedback
    data class DialogNode(val visible: Boolean, val title: String?, val content: FeatherNode, val confirmText: String, val dismissText: String?, val confirmAction: String, val dismissAction: String?) : FeatherNode
    data class BottomSheetNode(val visible: Boolean, val content: FeatherNode, val dismissAction: String?, val style: FeatherStyle? = null) : FeatherNode
    data class SnackbarNode(val message: String, val visible: Boolean, val actionText: String?, val action: String?) : FeatherNode
    data class BadgeNode(val count: Int, val color: String?, val child: FeatherNode?) : FeatherNode
    data class ChipNode(val label: String, val selected: Boolean?, val action: String?, val icon: String?, val color: String?) : FeatherNode
    data class TooltipNode(val message: String, val child: FeatherNode) : FeatherNode
    data class FloatingActionButtonNode(val icon: String, val action: String, val background: String?, val iconColor: String?) : FeatherNode
    data class PlaceholderNode(val width: String?, val height: String?, val cornerRadius: Int?) : FeatherNode

    // Scaffold
    data class ScaffoldNode(val topBar: FeatherNode?, val bottomBar: FeatherNode?, val floatingActionButton: FeatherNode?, val drawer: FeatherNode?, val body: FeatherNode, val style: FeatherStyle? = null) : FeatherNode
    data class TopAppBarNode(val title: String, val subtitle: String?, val navigationIcon: String?, val navigationAction: String?, val actions: List<FeatherNode>?, val background: String?, val contentColor: String?) : FeatherNode
    data class BottomAppBarNode(val children: List<FeatherNode>, val background: String?, val contentColor: String?) : FeatherNode
    data class TabRowNode(val tabs: List<TabItemData>, val selected: Int, val action: String, val background: String?, val contentColor: String?) : FeatherNode
    data class PagerNode(val pages: List<FeatherNode>, val currentPage: Int?, val action: String?, val style: FeatherStyle? = null) : FeatherNode
    data class DrawerNode(val header: FeatherNode?, val items: List<DrawerItemData>, val style: FeatherStyle? = null) : FeatherNode

    // Navigation
    data class NavigatorNode(val initialRoute: String, val screens: Map<String, FeatherNode>) : FeatherNode
    data class BottomTabsNode(val tabs: List<FeatherNode>, val style: FeatherStyle? = null) : FeatherNode
    data class TabNode(val title: String, val route: String, val icon: String?, val content: FeatherNode) : FeatherNode

    // Logic (Pre-resolved by Core, but kept for future dynamic work)
    data class ForEachNode(val itemsKey: String, val template: JSONObject) : FeatherNode
    data class ConditionNode(val key: String, val then: FeatherNode, val otherwise: FeatherNode?) : FeatherNode

    // Media
    data class VideoPlayerNode(val src: String, val autoplay: Boolean = false, val controls: Boolean = true, val loop: Boolean = false, val poster: String? = null, val style: FeatherStyle? = null) : FeatherNode
    data class AudioPlayerNode(val src: String, val autoplay: Boolean = false, val controls: Boolean = true, val loop: Boolean = false, val style: FeatherStyle? = null) : FeatherNode

    // Web
    data class WebViewNode(val url: String, val style: FeatherStyle? = null) : FeatherNode

    // Gestures
    data class GestureDetectorNode(val onTap: String?, val onPan: String?, val child: FeatherNode) : FeatherNode
    data class ClickableNode(val action: String, val child: FeatherNode, val ripple: Boolean = true) : FeatherNode
    data class LongPressableNode(val action: String, val child: FeatherNode, val duration: Int = 500) : FeatherNode
    data class PullToRefreshNode(val action: String, val child: FeatherNode, val refreshing: Boolean) : FeatherNode
    data class CustomNode(val module: String, val name: String, val props: JSONObject, val style: FeatherStyle? = null) : FeatherNode
}

data class RadioOptionData(val value: String, val label: String, val color: String?)
data class TabItemData(val title: String, val icon: String?)
data class DrawerItemData(val title: String, val icon: String?, val route: String?, val action: String?)


// MARK: - Activity

class MainActivity : ComponentActivity() {

    private val devServerBaseUrl = "http://10.0.2.2:8080"
    private val devServerSocketUrl = "ws://10.0.2.2:8080/ws"
    private var rootNode by mutableStateOf<FeatherNode>(
        FeatherNode.TextNode("Waiting for Feather CLI...")
    )
    private var diagnosticErrors by mutableStateOf<List<String>>(emptyList())
    private val client = OkHttpClient()
    private val moduleRegistry = ModuleRegistry()
    private var devSocket: WebSocket? = null

    // ─── WASM Runtime ────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerOfficialModules()

        setContent {
            CompositionLocalProvider(LocalModuleRegistry provides moduleRegistry) {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        FeatherRenderer(
                            node = rootNode,
                            onAction = ::sendAction,
                        )

                        if (diagnosticErrors.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Red.copy(alpha = 0.95f))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Feather Diagnostics",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                diagnosticErrors.forEach { errorMsg ->
                                    Text(
                                        text = errorMsg,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        try {
            // ─── Priority 1: WASM Runtime ─────────────────────────────────────────
            // ─── Priority 2: AOT JSON Bundle ──────────────────────────────────────
            val hasLocalBundle = assets.list("feather")?.contains("app.feather.json") == true
            if (hasLocalBundle) {
                assets.open("feather/app.feather.json").bufferedReader().use { reader ->
                    val renderPayload = reader.readText()
                    val (root, errors) = parseRenderMessage(renderPayload, moduleRegistry)
                    rootNode = root
                    diagnosticErrors = errors
                    return
                }
            }
        } catch (e: Exception) {
            diagnosticErrors = listOf("Failed to load bundle: ${e.message}")
        }

        // ─── Priority 3: Dev Server ───────────────────────────────────────────
        connectToDevServer()
        fetchBundleRender()
    }

    private fun connectToDevServer() {
        val request = Request.Builder().url(devServerSocketUrl).build()
        devSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                CoroutineScope(Dispatchers.Main).launch {
                    rootNode = FeatherNode.TextNode("Connected to Dev Server!")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (json?.optString("type") == "module_call") {
                        handleStructuredModuleCall(json)
                    } else {
                        val (root, errors) = parseRenderMessage(text, moduleRegistry)
                        rootNode = root
                        diagnosticErrors = errors
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                CoroutineScope(Dispatchers.Main).launch {
                    rootNode = FeatherNode.TextNode("Connection Failed: ${t.message}")
                }
            }
        })
    }

    private fun fetchBundleRender() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val manifestRequest = Request.Builder()
                    .url("$devServerBaseUrl/bundle/manifest")
                    .build()
                val manifestPayload = client.newCall(manifestRequest).execute().use { response ->
                    response.body?.string().orEmpty()
                }
                val manifest = JSONObject(manifestPayload)
                if (manifest.getString("sdk_version") != "1.0.0") {
                    error("Unsupported SDK: ${manifest.getString("sdk_version")}")
                }
                if (manifest.getInt("protocol_version") != 1) {
                    error("Unsupported protocol: ${manifest.getInt("protocol_version")}")
                }

                val renderPath = manifest.getJSONObject("render").getString("path")
                val renderRequest = Request.Builder()
                    .url("$devServerBaseUrl/bundle/artifact/$renderPath")
                    .build()
                client.newCall(renderRequest).execute().use { response ->
                    response.body?.string().orEmpty()
                }
            }.onSuccess { renderPayload ->
                CoroutineScope(Dispatchers.Main).launch {
                    val (root, errors) = parseRenderMessage(renderPayload, moduleRegistry)
                    rootNode = root
                    diagnosticErrors = errors
                }
            }.onFailure { error ->
                CoroutineScope(Dispatchers.Main).launch {
                    rootNode = FeatherNode.TextNode("Bundle fetch failed: ${error.message}")
                    diagnosticErrors = listOf(error.message ?: "Unknown error")
                }
            }
        }
    }

    private fun sendAction(action: String, payload: String?) {
        // ─── WASM Runtime Actions ─────────────────────────────────────────────
        // ─── Dev Server Actions ───────────────────────────────────────────────
        if (action.startsWith("host:")) {
            handleHostAction(action)
            return
        }
        val event = JSONObject()
            .put("type", "action")
            .put("action", action)
            .put("payload", payload)
        devSocket?.send(event.toString())
    }

    private fun handleHostAction(action: String) {
        val command = action.removePrefix("host:")
        if (moduleRegistry.handleLegacyCommand(command, moduleContext())) {
            return
        }
        
        // Fallback for unhandled host commands
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

// MARK: - Color Parsing

private fun parseHexColor(hex: String?): Color? {
    if (hex == null || hex.isEmpty()) return null
    return try {
        val cleaned = hex.removePrefix("#")
        val colorLong = when (cleaned.length) {
            3 -> "FF${cleaned[0]}${cleaned[0]}${cleaned[1]}${cleaned[1]}${cleaned[2]}${cleaned[2]}".toLong(16)
            6 -> "FF$cleaned".toLong(16)
            8 -> cleaned.toLong(16)
            else -> return null
        }
        Color(colorLong.toInt())
    } catch (_: Exception) {
        null
    }
}


// MARK: - Style Application Helpers

fun Modifier.applyContainerStyle(style: FeatherStyle?): Modifier {
    if (style == null) return this
    var mod = this

    style.opacity?.let { mod = mod.alpha(it) }
    style.margin?.let { mod = mod.padding(it.dp) }
    
    // Padding
    val p = style.padding
    val pt = style.paddingTop ?: style.paddingVertical ?: p
    val pb = style.paddingBottom ?: style.paddingVertical ?: p
    val ps = style.paddingStart ?: style.paddingHorizontal ?: p
    val pe = style.paddingEnd ?: style.paddingHorizontal ?: p
    if (pt != null || pb != null || ps != null || pe != null) {
        mod = mod.padding(
            start = (ps ?: 0).dp,
            top = (pt ?: 0).dp,
            end = (pe ?: 0).dp,
            bottom = (pb ?: 0).dp
        )
    }

    val shape = style.cornerRadius?.let { RoundedCornerShape(it.dp) }

    style.borderWidth?.let { bw ->
        val borderColor = parseHexColor(style.borderColor) ?: Color.Gray
        mod = mod.border(bw.dp, borderColor, shape ?: RoundedCornerShape(0.dp))
    }

    style.background?.let { bg ->
        val bgColor = parseHexColor(bg)
        if (bgColor != null) {
            mod = if (shape != null) mod.clip(shape).background(bgColor)
            else mod.background(bgColor)
        }
    }

    style.width?.let { w ->
        when (w) {
            "match", "fill" -> mod = mod.fillMaxWidth()
            "wrap" -> { /* Default */ }
            else -> w.toIntOrNull()?.let { mod = mod.width(it.dp) }
        }
    }
    style.height?.let { h ->
        when (h) {
            "match", "fill" -> mod = mod.fillMaxHeight()
            "wrap" -> { /* Default */ }
            else -> h.toIntOrNull()?.let { mod = mod.height(it.dp) }
        }
    }

    style.minWidth?.toIntOrNull()?.let { mod = mod.widthIn(min = it.dp) }
    style.minHeight?.toIntOrNull()?.let { mod = mod.heightIn(min = it.dp) }
    style.maxWidth?.toIntOrNull()?.let { mod = mod.widthIn(max = it.dp) }
    style.maxHeight?.toIntOrNull()?.let { mod = mod.heightIn(max = it.dp) }

    if (style.fillMaxWidth == true) mod = mod.fillMaxWidth()
    if (style.fillMaxHeight == true) mod = mod.fillMaxHeight()
    style.aspectRatio?.let { mod = mod.aspectRatio(it) }
    style.clip?.let { if (it) mod = mod.clip(shape ?: RoundedCornerShape(0.dp)) }

    if (style.position == "absolute") {
        mod = mod.offset(
            x = (style.left ?: 0f).dp,
            y = (style.top ?: 0f).dp
        )
    }
    style.zIndex?.let { mod = mod.zIndex(it) }

    return mod
}

private fun buildTextStyle(style: FeatherStyle?): TextStyle {
    if (style == null) return TextStyle.Default
    return TextStyle(
        color = parseHexColor(style.color ?: style.textColor) ?: Color.Unspecified,
        fontSize = style.fontSize?.sp ?: TextStyle.Default.fontSize,
        fontFamily = style.fontFamily?.let { FontFamily.Default },
        fontWeight = when (style.fontWeight) {
            "bold" -> FontWeight.Bold
            "100" -> FontWeight.Thin
            "200" -> FontWeight.ExtraLight
            "300" -> FontWeight.Light
            "400" -> FontWeight.Normal
            "500" -> FontWeight.Medium
            "600" -> FontWeight.SemiBold
            "700" -> FontWeight.Bold
            "800" -> FontWeight.ExtraBold
            "900" -> FontWeight.Black
            else -> null
        },
        fontStyle = when (style.fontStyle) {
            "italic" -> FontStyle.Italic
            else -> null
        },
        textAlign = when (style.textAlign) {
            "center" -> TextAlign.Center
            "right", "end" -> TextAlign.End
            "left", "start" -> TextAlign.Start
            else -> null
        },
        textDecoration = when (style.textDecoration) {
            "underline" -> TextDecoration.Underline
            "line-through" -> TextDecoration.LineThrough
            else -> null
        },
        letterSpacing = style.fontSize?.let { it.toFloat() * 0.05f }?.sp ?: TextUnit.Unspecified,
    )
}


// MARK: - Renderer

@Composable
private fun FeatherRenderer(
    node: FeatherNode,
    onAction: (String, String?) -> Unit,
) {
    when (node) {
        // ── Basic ──
        is FeatherNode.TextNode -> {
            Text(
                text = node.text,
                style = buildTextStyle(node.style),
                maxLines = node.style?.maxLines ?: Int.MAX_VALUE,
                overflow = if (node.style?.maxLines != null) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = Modifier.applyContainerStyle(node.style),
            )
        }
        is FeatherNode.ButtonNode -> {
            val bgColor = parseHexColor(node.style?.background)
            val txtColor = parseHexColor(node.style?.textColor ?: node.style?.color) ?: Color.White
            val shape = RoundedCornerShape((node.style?.cornerRadius ?: 4).dp)

            Button(
                onClick = { onAction(node.action, null) },
                enabled = node.enabled,
                colors = if (bgColor != null)
                    ButtonDefaults.buttonColors(backgroundColor = bgColor)
                else ButtonDefaults.buttonColors(),
                shape = shape,
                modifier = Modifier.applyContainerStyle(node.style),
            ) {
                Text(
                    text = node.text,
                    color = txtColor,
                    fontSize = node.style?.fontSize?.sp ?: MaterialTheme.typography.button.fontSize,
                    fontWeight = if (node.style?.fontWeight == "bold") FontWeight.Bold else null,
                )
            }
        }
        is FeatherNode.TextInputNode -> {
            var value by remember(node.action) { mutableStateOf(node.value) }
            val shape = RoundedCornerShape((node.style?.cornerRadius ?: 4).dp)

            Column(modifier = Modifier.applyContainerStyle(node.style)) {
                if (node.label != null) {
                    Text(
                        text = node.label,
                        style = buildTextStyle(null).copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                TextField(
                    value = value,
                    onValueChange = { nextValue ->
                        value = nextValue
                        onAction(node.action, nextValue)
                    },
                    placeholder = {
                        Text(
                            text = node.placeholder,
                            color = parseHexColor(node.style?.placeholderColor) ?: Color.Gray,
                        )
                    },
                    textStyle = buildTextStyle(node.style),
                    shape = shape,
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = parseHexColor(node.style?.background) ?: Color.Transparent,
                    ),
                    visualTransformation = if (node.style?.secure == true) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (node.style?.keyboardType) {
                            "number" -> KeyboardType.Number
                            "email" -> KeyboardType.Email
                            "phone" -> KeyboardType.Phone
                            else -> KeyboardType.Text
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is FeatherNode.ImageNode -> {
            AsyncImage(
                model = node.src,
                contentDescription = node.alt,
                contentScale = when (node.style?.contentMode) {
                    "fit" -> ContentScale.Fit
                    "crop" -> ContentScale.Crop
                    "fill" -> ContentScale.FillBounds
                    else -> ContentScale.Crop
                },
                modifier = Modifier.applyContainerStyle(node.style),
            )
        }
        is FeatherNode.IconNode -> {
            val icon = remember(node.name) { getIconByName(node.name) }
            val tint = parseHexColor(node.color) ?: Color.Unspecified
            val mod = if (node.action != null) Modifier.clickable { onAction(node.action, null) } else Modifier
            Icon(
                imageVector = icon,
                contentDescription = node.name,
                tint = tint,
                modifier = mod.size(node.size.dp)
            )
        }
        is FeatherNode.IconButtonNode -> {
            val icon = remember(node.icon) { getIconByName(node.icon) }
            val tint = parseHexColor(node.color) ?: Color.Unspecified
            val bg = parseHexColor(node.background)
            IconButton(
                onClick = { onAction(node.action, null) },
                modifier = if (bg != null) Modifier.background(bg, CircleShape) else Modifier
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = node.icon,
                    tint = tint,
                    modifier = Modifier.size(node.size.dp)
                )
            }
        }
        is FeatherNode.SpacerNode -> {
            Spacer(modifier = Modifier.weight(node.flex.toFloat().coerceAtLeast(0.01f)))
        }
        is FeatherNode.DividerNode -> {
            Divider(
                color = parseHexColor(node.color) ?: MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                thickness = node.thickness.dp,
                modifier = Modifier.padding(horizontal = node.indent.dp)
            )
        }

        // ── Layouts ──
        is FeatherNode.ColumnNode -> {
            val spacing = node.style?.spacing ?: 0
            val alignment = when (node.style?.alignment) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.dp),
                horizontalAlignment = alignment,
                modifier = Modifier.applyContainerStyle(node.style),
            ) {
                node.children.forEach { child ->
                    FeatherRenderer(child, onAction)
                }
            }
        }
        is FeatherNode.RowNode -> {
            val spacing = node.style?.spacing ?: 0
            val alignment = when (node.style?.alignment) {
                "center" -> Alignment.CenterVertically
                "end" -> Alignment.Bottom
                else -> Alignment.Top
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.dp),
                verticalAlignment = alignment,
                modifier = Modifier.applyContainerStyle(node.style),
            ) {
                node.children.forEach { child ->
                    FeatherRenderer(child, onAction)
                }
            }
        }
        is FeatherNode.BoxNode -> {
            val alignment = when (node.style?.alignment) {
                "center" -> Alignment.Center
                "end", "bottomEnd" -> Alignment.BottomEnd
                "topEnd" -> Alignment.TopEnd
                else -> Alignment.TopStart
            }
            Box(
                contentAlignment = alignment,
                modifier = Modifier.applyContainerStyle(node.style),
            ) {
                node.children.forEach { child ->
                    FeatherRenderer(child, onAction)
                }
            }
        }
        is FeatherNode.LazyColumnNode -> {
            val spacing = node.style?.spacing ?: 0
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.dp),
                modifier = Modifier.applyContainerStyle(node.style),
            ) {
                items(node.items) { child ->
                    FeatherRenderer(child, onAction)
                }
            }
        }
        is FeatherNode.LazyRowNode -> {
            val spacing = node.style?.spacing ?: 0
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.dp),
                modifier = Modifier.applyContainerStyle(node.style),
            ) {
                items(node.items) { child ->
                    FeatherRenderer(child, onAction)
                }
            }
        }
        is FeatherNode.LazyGridNode -> {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(node.columns),
                verticalArrangement = Arrangement.spacedBy((node.style?.spacing ?: 0).dp),
                horizontalArrangement = Arrangement.spacedBy((node.style?.spacing ?: 0).dp),
                modifier = Modifier.applyContainerStyle(node.style),
            ) {
                items(node.items.size) { index ->
                    FeatherRenderer(node.items[index], onAction)
                }
            }
        }
        is FeatherNode.ScrollViewNode -> {
            val scrollState = rememberScrollState()
            if (node.direction == "horizontal") {
                Row(
                    modifier = Modifier
                        .applyContainerStyle(node.style)
                        .horizontalScroll(scrollState),
                ) {
                    node.children.forEach { child -> FeatherRenderer(child, onAction) }
                }
            } else {
                Column(
                    modifier = Modifier
                        .applyContainerStyle(node.style)
                        .verticalScroll(scrollState),
                ) {
                    node.children.forEach { child -> FeatherRenderer(child, onAction) }
                }
            }
        }
        is FeatherNode.CardNode -> {
            androidx.compose.material.Card(
                elevation = (node.style?.elevation ?: 1).dp,
                shape = RoundedCornerShape((node.style?.cornerRadius ?: 4).dp),
                modifier = Modifier
                    .applyContainerStyle(node.style)
                    .let {
                        if (node.action != null) it.clickable { onAction(node.action, null) }
                        else it
                    }
            ) {
                Column {
                    node.children.forEach { child -> FeatherRenderer(child, onAction) }
                }
            }
        }
        is FeatherNode.SurfaceNode -> {
            androidx.compose.material.Surface(
                elevation = (node.style?.elevation ?: 0).dp,
                shape = RoundedCornerShape((node.style?.cornerRadius ?: 0).dp),
                color = parseHexColor(node.style?.background) ?: MaterialTheme.colors.surface,
                modifier = Modifier.applyContainerStyle(node.style)
            ) {
                Column {
                    node.children.forEach { child -> FeatherRenderer(child, onAction) }
                }
            }
        }
        is FeatherNode.AspectRatioNode -> {
            Box(modifier = Modifier.aspectRatio(node.ratio)) {
                FeatherRenderer(node.child, onAction)
            }
        }

        // ── Interactive ──
        is FeatherNode.SwitchNode -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                if (node.label != null) Text(text = node.label, modifier = Modifier.weight(1f))
                Switch(
                    checked = node.checked,
                    onCheckedChange = { onAction(node.action, it.toString()) },
                    enabled = node.enabled,
                    colors = androidx.compose.material.SwitchDefaults.colors(
                        checkedTrackColor = parseHexColor(node.trackColor) ?: MaterialTheme.colors.secondary
                    )
                )
            }
        }
        is FeatherNode.CheckboxNode -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                if (node.label != null) Text(text = node.label, modifier = Modifier.weight(1f))
                Checkbox(
                    checked = node.checked,
                    onCheckedChange = { onAction(node.action, it.toString()) },
                    enabled = node.enabled,
                    colors = androidx.compose.material.CheckboxDefaults.colors(
                        checkedColor = parseHexColor(node.color) ?: MaterialTheme.colors.secondary
                    )
                )
            }
        }
        is FeatherNode.SliderNode -> {
            Column(modifier = Modifier.padding(8.dp)) {
                if (node.label != null) Text(text = node.label)
                Slider(
                    value = node.value,
                    onValueChange = { onAction(node.action, it.toString()) },
                    valueRange = node.min..node.max,
                    steps = node.step?.let { ((node.max - node.min) / it).toInt() - 1 } ?: 0
                )
            }
        }

        // ── Progress ──
        is FeatherNode.LinearProgressNode -> {
            if (node.progress != null) {
                LinearProgressIndicator(progress = node.progress, color = parseHexColor(node.color) ?: MaterialTheme.colors.primary)
            } else {
                LinearProgressIndicator(color = parseHexColor(node.color) ?: MaterialTheme.colors.primary)
            }
        }
        is FeatherNode.CircularProgressNode -> {
            if (node.progress != null) {
                CircularProgressIndicator(progress = node.progress, color = parseHexColor(node.color) ?: MaterialTheme.colors.primary, strokeWidth = node.strokeWidth.dp)
            } else {
                CircularProgressIndicator(color = parseHexColor(node.color) ?: MaterialTheme.colors.primary, strokeWidth = node.strokeWidth.dp)
            }
        }

        // ── Scaffold ──
        is FeatherNode.ScaffoldNode -> {
            val snackbarHostState = remember { SnackbarHostState() }
            Scaffold(
                topBar = { node.topBar?.let { FeatherRenderer(it, onAction) } },
                bottomBar = { node.bottomBar?.let { FeatherRenderer(it, onAction) } },
                floatingActionButton = { node.floatingActionButton?.let { FeatherRenderer(it, onAction) } },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.applyContainerStyle(node.style)
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    FeatherRenderer(node.body, onAction)
                }
            }
        }
        is FeatherNode.TopAppBarNode -> {
            TopAppBar(
                title = { Text(text = node.title) },
                navigationIcon = node.navigationIcon?.let { iconName ->
                    {
                        IconButton(onClick = { node.navigationAction?.let { onAction(it, null) } }) {
                            Icon(getIconByName(iconName), contentDescription = null)
                        }
                    }
                },
                backgroundColor = parseHexColor(node.background) ?: MaterialTheme.colors.primarySurface,
                contentColor = parseHexColor(node.contentColor) ?: androidx.compose.material.contentColorFor(MaterialTheme.colors.primarySurface)
            )
        }

        // ── Navigation ──
        is FeatherNode.NavigatorNode -> {
            var currentRoute by remember(node.initialRoute) { mutableStateOf(node.initialRoute) }
            val screen = node.screens[currentRoute] ?: node.screens.values.first()
            FeatherRenderer(screen) { action, payload ->
                if (action == "__navigate") payload?.let { currentRoute = it }
                else onAction(action, payload)
            }
        }
        
        // ── Feedback / Overlay ──
        is FeatherNode.DialogNode -> {
            if (node.visible) {
                androidx.compose.material.AlertDialog(
                    onDismissRequest = { node.dismissAction?.let { onAction(it, null) } },
                    title = node.title?.let { { Text(it) } },
                    text = { FeatherRenderer(node.content, onAction) },
                    confirmButton = {
                        Button(onClick = { onAction(node.confirmAction, null) }) {
                            Text(node.confirmText)
                        }
                    },
                    dismissButton = node.dismissText?.let { dt ->
                        {
                            Button(onClick = { node.dismissAction?.let { onAction(it, null) } }) {
                                Text(dt)
                            }
                        }
                    }
                )
            }
        }
        is FeatherNode.BottomSheetNode -> {
            if (node.visible) {
                Box(
                    modifier = Modifier.fillMaxWidth().applyContainerStyle(node.style),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    FeatherRenderer(node.content, onAction)
                }
            }
        }
        is FeatherNode.SnackbarNode -> {
            if (node.visible) {
                androidx.compose.material.Snackbar(
                    action = node.actionText?.let { at ->
                        { Button(onClick = { node.action?.let { onAction(it, null) } }) { Text(at) } }
                    }
                ) {
                    Text(node.message)
                }
            }
        }
        is FeatherNode.BadgeNode -> {
            Box(contentAlignment = Alignment.TopEnd) {
                node.child?.let { FeatherRenderer(it, onAction) }
                if (node.count > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(parseHexColor(node.color) ?: Color.Red, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (node.count > 99) "99+" else node.count.toString(),
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
        is FeatherNode.ChipNode -> {
            val bg = if (node.selected == true) {
                parseHexColor(node.color) ?: MaterialTheme.colors.primary
            } else {
                MaterialTheme.colors.surface
            }
            Box(
                modifier = Modifier
                    .background(bg, RoundedCornerShape(16.dp))
                    .clickable { node.action?.let { onAction(it, null) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = node.label, color = if (node.selected == true) Color.White else Color.Unspecified, fontSize = 14.sp)
            }
        }
        is FeatherNode.TooltipNode -> {
            // Tooltip: render child; tooltip message shown via long-press in a real impl
            FeatherRenderer(node.child, onAction)
        }
        is FeatherNode.FloatingActionButtonNode -> {
            FloatingActionButton(
                onClick = { onAction(node.action, null) },
                backgroundColor = parseHexColor(node.background) ?: MaterialTheme.colors.secondary,
                contentColor = parseHexColor(node.iconColor) ?: MaterialTheme.colors.onSecondary
            ) {
                Icon(getIconByName(node.icon), contentDescription = null)
            }
        }
        is FeatherNode.PlaceholderNode -> {
            val w = node.width?.toIntOrNull()?.dp ?: 100.dp
            val h = node.height?.toIntOrNull()?.dp ?: 20.dp
            val cr = node.cornerRadius?.dp ?: 4.dp
            Box(
                modifier = Modifier
                    .width(w)
                    .height(h)
                    .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(cr))
            )
        }

        // ── Additional Scaffold Parts ──
        is FeatherNode.BottomAppBarNode -> {
            androidx.compose.material.BottomAppBar(
                backgroundColor = parseHexColor(node.background) ?: MaterialTheme.colors.primarySurface,
                contentColor = parseHexColor(node.contentColor) ?: androidx.compose.material.contentColorFor(MaterialTheme.colors.primarySurface)
            ) {
                node.children.forEach { child -> FeatherRenderer(child, onAction) }
            }
        }
        is FeatherNode.TabRowNode -> {
            // Simple TabRow rendering
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(parseHexColor(node.background) ?: MaterialTheme.colors.primarySurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                node.tabs.forEachIndexed { index, tab ->
                    val isSelected = index == node.selected
                    Text(
                        text = tab.title,
                        color = if (isSelected) {
                            parseHexColor(node.contentColor) ?: MaterialTheme.colors.onPrimary
                        } else {
                            (parseHexColor(node.contentColor) ?: MaterialTheme.colors.onPrimary).copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .clickable { onAction(node.action, index.toString()) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        is FeatherNode.PagerNode -> {
            // Simple pager: render current page
            val page = node.currentPage ?: 0
            if (page < node.pages.size) {
                Box(modifier = Modifier.applyContainerStyle(node.style)) {
                    FeatherRenderer(node.pages[page], onAction)
                }
            }
        }
        is FeatherNode.DrawerNode -> {
            Column(modifier = Modifier.applyContainerStyle(node.style)) {
                node.header?.let { FeatherRenderer(it, onAction) }
                node.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.action?.let { onAction(it, null) } }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.icon?.let { iconName ->
                            Icon(getIconByName(iconName), contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                        }
                        Text(text = item.title)
                    }
                }
            }
        }

        // ── Interactive (remaining) ──
        is FeatherNode.RadioGroupNode -> {
            Column {
                node.options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(node.action, option.value) }
                            .padding(8.dp)
                    ) {
                        androidx.compose.material.RadioButton(
                            selected = option.value == node.selected,
                            onClick = { onAction(node.action, option.value) }
                        )
                        Text(text = option.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        is FeatherNode.DropdownMenuNode -> {
            var expanded by remember { mutableStateOf(false) }
            Column {
                node.label?.let { Text(text = it, fontSize = 12.sp, color = Color.Gray) }
                Box {
                    Text(
                        text = node.selected.ifEmpty { "Select..." },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    )
                    androidx.compose.material.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        node.options.forEach { option ->
                            androidx.compose.material.DropdownMenuItem(
                                onClick = {
                                    expanded = false
                                    onAction(node.action, option)
                                }
                            ) {
                                Text(option)
                            }
                        }
                    }
                }
            }
        }

        // ── Gesture Wrappers ──
        is FeatherNode.GestureDetectorNode -> {
            Box(
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        node.onPan?.let {
                            val payload = "{\"action\": \"$it\", \"payload\": {\"x\": ${dragAmount.x}, \"y\": ${dragAmount.y}}}"
                            onAction(payload, null)
                        }
                    }
                }.clickable {
                    node.onTap?.let { onAction(it, null) }
                }
            ) {
                FeatherRenderer(node.child, onAction)
            }
        }
        is FeatherNode.ClickableNode -> {
            Box(modifier = Modifier.clickable(enabled = node.ripple) { onAction(node.action, null) }) {
                FeatherRenderer(node.child, onAction)
            }
        }
        is FeatherNode.LongPressableNode -> {
            Box(modifier = Modifier.clickable { onAction(node.action, null) }) {
                FeatherRenderer(node.child, onAction)
            }
        }
        is FeatherNode.PullToRefreshNode -> {
            // Simple pull-to-refresh placeholder — real impl needs SwipeRefreshLayout
            Box(modifier = Modifier.fillMaxSize()) {
                FeatherRenderer(node.child, onAction)
                if (node.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(16.dp))
                }
            }
        }

        // ── Logic ──
        is FeatherNode.ForEachNode -> {
            // ForEach resolves at runtime in Rust core; show placeholder
            Text(text = "ForEach: \${${node.itemsKey}}", color = Color.Gray)
        }
        is FeatherNode.ConditionNode -> {
            // Condition resolves at runtime in Rust core; render `then` branch
            FeatherRenderer(node.then, onAction)
        }

        // ── Media ──
        is FeatherNode.AudioPlayerNode -> {
            val module = LocalModuleRegistry.current?.rendererFor(node)
            if (module != null) {
                module.Render(node, onAction)
            } else {
                Text(text = "Missing module: feather-media")
            }
        }
        is FeatherNode.VideoPlayerNode -> {
            val module = LocalModuleRegistry.current?.rendererFor(node)
            if (module != null) {
                module.Render(node, onAction)
            } else {
                Text(text = "Missing module: feather-media")
            }
        }
        is FeatherNode.WebViewNode -> {
            val module = LocalModuleRegistry.current?.rendererFor(node)
            if (module != null) {
                module.Render(node, onAction)
            } else {
                Text(text = "Missing module: feather-webview")
            }
        }
        is FeatherNode.CustomNode -> {
            val module = LocalModuleRegistry.current?.rendererFor(node)
            if (module != null) {
                module.Render(node, onAction)
            } else {
                Text(text = "Missing module: ${node.module}")
            }
        }

        else -> {
            Text(text = "Renderer for ${node.javaClass.simpleName} not implemented")
        }
    }
}

// MARK: - JSON Parsing

private fun parseRenderMessage(payload: String, registry: ModuleRegistry): Pair<FeatherNode, List<String>> {
    return runCatching {
        val message = JSONObject(payload)
        val root = parseNode(message.getJSONObject("root"))
        val type = message.optString("type", "render")
        val errors = mutableListOf<String>()
        if (type == "render_error") {
            val diagnostics = message.optJSONArray("diagnostics")
            if (diagnostics != null) {
                for (i in 0 until diagnostics.length()) {
                    val diag = diagnostics.optJSONObject(i)
                    diag?.optString("message")?.let { errors.add(it) }
                }
            }
        }
        
        val missingModules = mutableSetOf<String>()
        collectMissingModules(root, registry, missingModules)
        missingModules.forEach { name ->
            errors.add("Missing required module: $name")
        }
        
        Pair(root, errors)
    }.getOrElse { error ->
        Pair(FeatherNode.TextNode("Invalid Feather payload: ${error.message}"), listOf(error.message ?: "Unknown error"))
    }
}

private fun collectMissingModules(node: FeatherNode, registry: ModuleRegistry, missing: MutableSet<String>) {
    when (node) {
        is FeatherNode.AudioPlayerNode -> if (!registry.hasModule("feather-media")) missing.add("feather-media")
        is FeatherNode.VideoPlayerNode -> if (!registry.hasModule("feather-media")) missing.add("feather-media")
        is FeatherNode.WebViewNode -> if (!registry.hasModule("feather-webview")) missing.add("feather-webview")
        is FeatherNode.CustomNode -> if (!registry.hasModule(node.module)) missing.add(node.module)
        else -> {}
    }

    when (node) {
        is FeatherNode.ColumnNode -> node.children.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.RowNode -> node.children.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.BoxNode -> node.children.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.LazyColumnNode -> node.items.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.LazyRowNode -> node.items.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.LazyGridNode -> node.items.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.ScrollViewNode -> node.children.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.CardNode -> node.children.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.SurfaceNode -> node.children.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.AspectRatioNode -> collectMissingModules(node.child, registry, missing)
        is FeatherNode.ScaffoldNode -> {
            node.topBar?.let { collectMissingModules(it, registry, missing) }
            node.bottomBar?.let { collectMissingModules(it, registry, missing) }
            node.floatingActionButton?.let { collectMissingModules(it, registry, missing) }
            node.drawer?.let { collectMissingModules(it, registry, missing) }
            collectMissingModules(node.body, registry, missing)
        }
        is FeatherNode.NavigatorNode -> node.screens.values.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.BottomTabsNode -> node.tabs.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.TabNode -> collectMissingModules(node.content, registry, missing)
        is FeatherNode.DialogNode -> collectMissingModules(node.content, registry, missing)
        is FeatherNode.BottomSheetNode -> collectMissingModules(node.content, registry, missing)
        is FeatherNode.BadgeNode -> node.child?.let { collectMissingModules(it, registry, missing) }
        is FeatherNode.TooltipNode -> collectMissingModules(node.child, registry, missing)
        is FeatherNode.BottomAppBarNode -> node.children.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.PagerNode -> node.pages.forEach { collectMissingModules(it, registry, missing) }
        is FeatherNode.DrawerNode -> node.header?.let { collectMissingModules(it, registry, missing) }
        is FeatherNode.GestureDetectorNode -> collectMissingModules(node.child, registry, missing)
        is FeatherNode.ClickableNode -> collectMissingModules(node.child, registry, missing)
        is FeatherNode.LongPressableNode -> collectMissingModules(node.child, registry, missing)
        is FeatherNode.PullToRefreshNode -> collectMissingModules(node.child, registry, missing)
        is FeatherNode.ConditionNode -> {
            collectMissingModules(node.then, registry, missing)
            node.otherwise?.let { collectMissingModules(it, registry, missing) }
        }
        else -> {}
    }
}

private fun parseStyle(json: JSONObject): FeatherStyle? {
    val styleJson = json.optJSONObject("style") ?: json
    val styleKeys = listOf(
        "color", "fontSize", "fontWeight", "fontStyle", "textAlign", "textDecoration",
        "fontFamily", "background", "padding", "paddingHorizontal", "paddingVertical",
        "paddingTop", "paddingBottom", "paddingStart", "paddingEnd", "margin",
        "cornerRadius", "borderColor", "borderWidth", "width", "height", "minWidth",
        "minHeight", "maxWidth", "maxHeight", "alignment", "spacing", "opacity",
        "textColor", "placeholderColor", "contentMode", "shadow", "elevation",
        "maxLines", "flex", "fillMaxWidth", "fillMaxHeight", "aspectRatio",
        "gradient", "clip", "keyboardType", "maxLength", "multiline", "secure",
        "direction", "position", "top", "bottom", "left", "right", "zIndex"
    )
    if (json.optJSONObject("style") == null && styleKeys.none { json.has(it) }) {
        return null
    }
    return FeatherStyle(
        color = styleJson.optStringOrNull("color"),
        fontSize = styleJson.optIntOrNull("fontSize"),
        fontWeight = styleJson.optStringOrNull("fontWeight"),
        fontStyle = styleJson.optStringOrNull("fontStyle"),
        textAlign = styleJson.optStringOrNull("textAlign"),
        textDecoration = styleJson.optStringOrNull("textDecoration"),
        fontFamily = styleJson.optStringOrNull("fontFamily"),
        background = styleJson.optStringOrNull("background"),
        padding = styleJson.optIntOrNull("padding"),
        paddingHorizontal = styleJson.optIntOrNull("paddingHorizontal"),
        paddingVertical = styleJson.optIntOrNull("paddingVertical"),
        paddingTop = styleJson.optIntOrNull("paddingTop"),
        paddingBottom = styleJson.optIntOrNull("paddingBottom"),
        paddingStart = styleJson.optIntOrNull("paddingStart"),
        paddingEnd = styleJson.optIntOrNull("paddingEnd"),
        margin = styleJson.optIntOrNull("margin"),
        cornerRadius = styleJson.optIntOrNull("cornerRadius"),
        borderColor = styleJson.optStringOrNull("borderColor"),
        borderWidth = styleJson.optIntOrNull("borderWidth"),
        width = styleJson.optStringOrNull("width"),
        height = styleJson.optStringOrNull("height"),
        minWidth = styleJson.optStringOrNull("minWidth"),
        minHeight = styleJson.optStringOrNull("minHeight"),
        maxWidth = styleJson.optStringOrNull("maxWidth"),
        maxHeight = styleJson.optStringOrNull("maxHeight"),
        alignment = styleJson.optStringOrNull("alignment"),
        spacing = styleJson.optIntOrNull("spacing"),
        opacity = styleJson.optDoubleOrNull("opacity")?.toFloat(),
        textColor = styleJson.optStringOrNull("textColor"),
        placeholderColor = styleJson.optStringOrNull("placeholderColor"),
        contentMode = styleJson.optStringOrNull("contentMode"),
        shadow = styleJson.optStringOrNull("shadow"),
        elevation = styleJson.optIntOrNull("elevation"),
        maxLines = styleJson.optIntOrNull("maxLines"),
        flex = styleJson.optIntOrNull("flex"),
        fillMaxWidth = if (styleJson.has("fillMaxWidth")) styleJson.optBoolean("fillMaxWidth") else null,
        fillMaxHeight = if (styleJson.has("fillMaxHeight")) styleJson.optBoolean("fillMaxHeight") else null,
        aspectRatio = styleJson.optDoubleOrNull("aspectRatio")?.toFloat(),
        gradient = styleJson.optStringOrNull("gradient"),
        clip = if (styleJson.has("clip")) styleJson.optBoolean("clip") else null,
        keyboardType = styleJson.optStringOrNull("keyboardType"),
        maxLength = styleJson.optIntOrNull("maxLength"),
        multiline = if (styleJson.has("multiline")) styleJson.optBoolean("multiline") else null,
        secure = if (styleJson.has("secure")) styleJson.optBoolean("secure") else null,
        direction = styleJson.optStringOrNull("direction"),
        position = styleJson.optStringOrNull("position"),
        top = styleJson.optDoubleOrNull("top")?.toFloat(),
        bottom = styleJson.optDoubleOrNull("bottom")?.toFloat(),
        left = styleJson.optDoubleOrNull("left")?.toFloat(),
        right = styleJson.optDoubleOrNull("right")?.toFloat(),
        zIndex = styleJson.optDoubleOrNull("zIndex")?.toFloat(),
    )
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null


private fun parseNode(json: JSONObject): FeatherNode {
    val style = parseStyle(json)
    return when (val type = json.getString("type")) {
        "Text" -> FeatherNode.TextNode(json.optString("text"), style)
        "Button" -> FeatherNode.ButtonNode(
            text = json.optString("text"),
            action = json.optString("action"),
            enabled = json.optBoolean("enabled", true),
            style = style,
        )
        "TextInput" -> FeatherNode.TextInputNode(
            value = json.optString("value"),
            placeholder = json.optString("placeholder"),
            action = json.optString("action"),
            label = json.optStringOrNull("label"),
            style = style,
        )
        "Icon" -> FeatherNode.IconNode(
            name = json.optString("name"),
            size = json.optInt("size", 24),
            color = json.optStringOrNull("color"),
            action = json.optStringOrNull("action"),
        )
        "IconButton" -> FeatherNode.IconButtonNode(
            icon = json.optString("icon"),
            action = json.optString("action"),
            size = json.optInt("size", 24),
            color = json.optStringOrNull("color"),
            background = json.optStringOrNull("background"),
        )
        "Divider" -> FeatherNode.DividerNode(
            color = json.optStringOrNull("color"),
            thickness = json.optInt("thickness", 1),
            indent = json.optInt("indent", 0),
        )
        "Column" -> {
            val children = json.optJSONArray("children")
            FeatherNode.ColumnNode(
                children = List(children?.length() ?: 0) { index ->
                    parseNode(children!!.getJSONObject(index))
                },
                style = style,
            )
        }
        "Row" -> {
            val children = json.optJSONArray("children")
            FeatherNode.RowNode(
                children = List(children?.length() ?: 0) { index ->
                    parseNode(children!!.getJSONObject(index))
                },
                style = style,
            )
        }
        "Box" -> {
            val children = json.optJSONArray("children")
            FeatherNode.BoxNode(
                children = List(children?.length() ?: 0) { index ->
                    parseNode(children!!.getJSONObject(index))
                },
                style = style,
            )
        }
        "LazyColumn" -> {
            val items = json.optJSONArray("items")
            FeatherNode.LazyColumnNode(
                items = List(items?.length() ?: 0) { index ->
                    parseNode(items!!.getJSONObject(index))
                },
                style = style,
            )
        }
        "LazyRow" -> {
            val items = json.optJSONArray("items")
            FeatherNode.LazyRowNode(
                items = List(items?.length() ?: 0) { index ->
                    parseNode(items!!.getJSONObject(index))
                },
                style = style,
            )
        }
        "LazyGrid" -> {
            val items = json.optJSONArray("items")
            FeatherNode.LazyGridNode(
                items = List(items?.length() ?: 0) { index ->
                    parseNode(items!!.getJSONObject(index))
                },
                columns = json.optInt("columns", 2),
                style = style,
            )
        }
        "ScrollView" -> {
            val children = json.optJSONArray("children")
            FeatherNode.ScrollViewNode(
                children = List(children?.length() ?: 0) { index ->
                    parseNode(children!!.getJSONObject(index))
                },
                direction = json.optString("direction", "vertical"),
                style = style,
            )
        }
        "Card" -> {
            val children = json.optJSONArray("children")
            FeatherNode.CardNode(
                children = List(children?.length() ?: 0) { index ->
                    parseNode(children!!.getJSONObject(index))
                },
                action = json.optStringOrNull("action"),
                style = style,
            )
        }
        "Surface" -> {
            val children = json.optJSONArray("children")
            FeatherNode.SurfaceNode(
                children = List(children?.length() ?: 0) { index ->
                    parseNode(children!!.getJSONObject(index))
                },
                style = style,
            )
        }
        "AspectRatio" -> FeatherNode.AspectRatioNode(
            ratio = json.optDouble("ratio", 1.0).toFloat(),
            child = parseNode(json.getJSONObject("child"))
        )
        "Switch" -> FeatherNode.SwitchNode(
            checked = json.optBoolean("checked"),
            action = json.optString("action"),
            enabled = json.optBoolean("enabled", true),
            label = json.optStringOrNull("label"),
            trackColor = json.optStringOrNull("trackColor"),
            thumbColor = json.optStringOrNull("thumbColor"),
        )
        "Checkbox" -> FeatherNode.CheckboxNode(
            checked = json.optBoolean("checked"),
            action = json.optString("action"),
            enabled = json.optBoolean("enabled", true),
            label = json.optStringOrNull("label"),
            color = json.optStringOrNull("color"),
        )
        "Slider" -> FeatherNode.SliderNode(
            value = json.optDouble("value", 0.0).toFloat(),
            min = json.optDouble("min", 0.0).toFloat(),
            max = json.optDouble("max", 1.0).toFloat(),
            step = if (json.has("step")) json.optDouble("step").toFloat() else null,
            action = json.optString("action"),
            label = json.optStringOrNull("label"),
            trackColor = json.optStringOrNull("trackColor"),
            thumbColor = json.optStringOrNull("thumbColor"),
        )
        "SearchBar" -> FeatherNode.SearchBarNode(
            value = json.optString("value"),
            placeholder = json.optString("placeholder"),
            action = json.optString("action"),
            style = style,
        )
        "LinearProgress" -> FeatherNode.LinearProgressNode(
            progress = if (json.has("progress")) json.optDouble("progress").toFloat() else null,
            color = json.optStringOrNull("color"),
            trackColor = json.optStringOrNull("trackColor"),
        )
        "CircularProgress" -> FeatherNode.CircularProgressNode(
            progress = if (json.has("progress")) json.optDouble("progress").toFloat() else null,
            color = json.optStringOrNull("color"),
            size = json.optInt("size", 40),
            strokeWidth = json.optInt("strokeWidth", 4),
        )
        "Scaffold" -> FeatherNode.ScaffoldNode(
            topBar = if (json.has("topBar")) parseNode(json.getJSONObject("topBar")) else null,
            bottomBar = if (json.has("bottomBar")) parseNode(json.getJSONObject("bottomBar")) else null,
            floatingActionButton = if (json.has("floatingActionButton")) parseNode(json.getJSONObject("floatingActionButton")) else null,
            drawer = if (json.has("drawer")) parseNode(json.getJSONObject("drawer")) else null,
            body = parseNode(json.getJSONObject("body")),
            style = style,
        )
        "TopAppBar" -> FeatherNode.TopAppBarNode(
            title = json.optString("title"),
            subtitle = json.optStringOrNull("subtitle"),
            navigationIcon = json.optStringOrNull("navigationIcon"),
            navigationAction = json.optStringOrNull("navigationAction"),
            actions = json.optJSONArray("actions")?.let { arr ->
                List(arr.length()) { parseNode(arr.getJSONObject(it)) }
            },
            background = json.optStringOrNull("background"),
            contentColor = json.optStringOrNull("contentColor"),
        )
        "Navigator" -> {
            val initial = json.optString("initialRoute", "home")
            val screensJson = json.optJSONObject("screens") ?: JSONObject()
            val screens = mutableMapOf<String, FeatherNode>()
            screensJson.keys().forEach { key ->
                screens[key] = parseNode(screensJson.getJSONObject(key))
            }
            FeatherNode.NavigatorNode(initial, screens)
        }
        "BottomTabs" -> {
            val tabsArray = json.optJSONArray("tabs") ?: org.json.JSONArray()
            FeatherNode.BottomTabsNode(
                tabs = List(tabsArray.length()) { index ->
                    parseNode(tabsArray.getJSONObject(index))
                },
                style = style
            )
        }
        "Image" -> FeatherNode.ImageNode(
            src = json.optString("src"),
            alt = json.optString("alt"),
            style = style,
        )
        "Spacer" -> FeatherNode.SpacerNode(
            flex = json.optInt("flex", 1),
        )
        "GestureDetector" -> FeatherNode.GestureDetectorNode(
            onTap = json.optStringOrNull("onTap"),
            onPan = json.optStringOrNull("onPan"),
            child = parseNode(json.getJSONObject("child")),
        )
        "Clickable" -> FeatherNode.ClickableNode(
            action = json.optString("action"),
            child = parseNode(json.getJSONObject("child")),
            ripple = json.optBoolean("ripple", true)
        )
        "VideoPlayer" -> FeatherNode.VideoPlayerNode(
            src = json.optString("src"),
            autoplay = json.optBoolean("autoplay"),
            controls = json.optBoolean("controls", true),
            loop = json.optBoolean("loop"),
            poster = json.optStringOrNull("poster"),
            style = style,
        )
        "AudioPlayer" -> FeatherNode.AudioPlayerNode(
            src = json.optString("src"),
            autoplay = json.optBoolean("autoplay"),
            controls = json.optBoolean("controls", true),
            loop = json.optBoolean("loop"),
            style = style,
        )
        "WebView" -> FeatherNode.WebViewNode(
            url = json.optString("url"),
            style = style,
        )
        "Custom" -> FeatherNode.CustomNode(
            module = json.optString("module"),
            name = json.optString("name", json.optString("widget")),
            props = json.optJSONObject("props") ?: JSONObject(),
            style = style,
        )
        else -> FeatherNode.TextNode("Unsupported widget: $type")
    }
}


private fun getIconByName(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (name) {
        "home" -> Icons.Default.Home
        "search" -> Icons.Default.Search
        "settings" -> Icons.Default.Settings
        "person", "profile", "account" -> Icons.Default.Person
        "menu" -> Icons.Default.Menu
        "back", "arrow_back" -> Icons.Default.ArrowBack
        "forward", "arrow_forward" -> Icons.Default.ArrowForward
        "add", "plus" -> Icons.Default.Add
        "close", "clear" -> Icons.Default.Close
        "check", "done" -> Icons.Default.Check
        "info" -> Icons.Default.Info
        "warning" -> Icons.Default.Warning
        "error" -> Icons.Default.Error
        "play", "play_arrow" -> Icons.Default.PlayArrow
        "pause" -> Icons.Default.Pause
        "stop" -> Icons.Default.Stop
        "skip_next" -> Icons.Default.SkipNext
        "skip_previous" -> Icons.Default.SkipPrevious
        "star" -> Icons.Default.Star
        "favorite", "heart" -> Icons.Default.Favorite
        "share" -> Icons.Default.Share
        "download" -> Icons.Default.Download
        "upload" -> Icons.Default.Upload
        "refresh" -> Icons.Default.Refresh
        "notifications", "bell" -> Icons.Default.Notifications
        "email", "mail" -> Icons.Default.Email
        "phone" -> Icons.Default.Phone
        "location", "place" -> Icons.Default.Place
        "camera" -> Icons.Default.PhotoCamera
        "image", "photo" -> Icons.Default.Image
        "video", "videocam" -> Icons.Default.Videocam
        "mic", "microphone" -> Icons.Default.Mic
        "send" -> Icons.Default.Send
        "edit" -> Icons.Default.Edit
        "delete", "trash" -> Icons.Default.Delete
        "more_vert" -> Icons.Default.MoreVert
        "more_horiz" -> Icons.Default.MoreHoriz
        else -> Icons.Default.Star // Fallback
    }
}
