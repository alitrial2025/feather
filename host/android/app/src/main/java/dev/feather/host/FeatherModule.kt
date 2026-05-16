package dev.feather.host

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import okhttp3.OkHttpClient
import org.json.JSONObject
import uniffi.feather_core.ModuleInfo
import uniffi.feather_core.NativeModule
import uniffi.feather_core.NativeCallback

val LocalModuleRegistry = staticCompositionLocalOf<ModuleRegistry?> { null }

interface FeatherModule {
    val name: String
    val version: String

    fun getWidgets(): List<String> = emptyList()
    fun canHandleLegacyCommand(command: String): Boolean
    fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean
    fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean = false
    fun canRender(node: FeatherNode): Boolean = false

    // Lifecycle hooks
    fun initialize(context: FeatherModuleContext) = Unit
    fun start() = Unit
    fun resume() = Unit
    fun pause() = Unit
    fun stop() = Unit
    fun destroy() = Unit
    
    // Callbacks
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean = false
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean = false

    @Composable
    fun Render(node: FeatherNode, onAction: (String, String?) -> Unit) = Unit
}

class NativeModuleWrapper(
    private val module: FeatherModule,
    private val context: FeatherModuleContext
) : NativeModule {
    override fun getInfo(): ModuleInfo {
        return ModuleInfo(module.name, module.version)
    }

    override fun getWidgets(): List<String> {
        return module.getWidgets()
    }

    override fun callSync(action: String, payload: String): String {
        val json = runCatching { JSONObject(payload) }.getOrElse { JSONObject() }
        if (module.call(action, json, context)) {
            return "{\"status\": \"success\"}"
        }
        throw uniffi.feather_core.ModuleException.ExecutionFailed("Module call failed")
    }

    override fun callAsync(action: String, payload: String, callback: NativeCallback) {
        // Simple synchronous delegation for now
        try {
            val result = callSync(action, payload)
            callback.onSuccess(result)
        } catch (e: Exception) {
            callback.onError(e.message ?: "Unknown error")
        }
    }
}


data class FeatherModuleContext(
    val appContext: Context,
    val httpClient: OkHttpClient,
    val sendStateAssignment: (String, String) -> Unit,
)

class ModuleRegistry {
    private val modules = linkedMapOf<String, FeatherModule>()

    fun register(module: FeatherModule) {
        modules[module.name] = module
    }

    fun registeredModules(): List<FeatherModule> = modules.values.toList()

    fun hasModule(name: String): Boolean = modules.containsKey(name)

    fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean {
        val module = modules.values.firstOrNull { it.canHandleLegacyCommand(command) }
        return module?.handleLegacyCommand(command, context) == true
    }

    fun call(moduleName: String, method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        return modules[moduleName]?.call(method, payload, context) == true
    }

    fun rendererFor(node: FeatherNode): FeatherModule? {
        return modules.values.firstOrNull { it.canRender(node) }
    }
    
    // Lifecycle broadcasts
    fun initializeAll(context: FeatherModuleContext) {
        modules.values.forEach { it.initialize(context) }
    }
    
    fun onStart() {
        modules.values.forEach { it.start() }
    }
    
    fun onResume() {
        modules.values.forEach { it.resume() }
    }
    
    fun onPause() {
        modules.values.forEach { it.pause() }
    }
    
    fun onStop() {
        modules.values.forEach { it.stop() }
    }
    
    fun onDestroy() {
        modules.values.forEach { it.destroy() }
    }
    
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        modules.values.forEach { it.onRequestPermissionsResult(requestCode, permissions, grantResults) }
    }
    
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        modules.values.forEach { it.onActivityResult(requestCode, resultCode, data) }
    }
}
