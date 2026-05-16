package dev.feather.host

import android.content.Context
import androidx.compose.runtime.Composable
import okhttp3.OkHttpClient

interface FeatherModule {
    val name: String
    val version: String

    fun canHandleLegacyCommand(command: String): Boolean
    fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean
    fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean = false
    fun canRender(node: FeatherNode): Boolean = false

    @Composable
    fun Render(node: FeatherNode, onAction: (String, String?) -> Unit) = Unit
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
}
