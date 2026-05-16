package dev.feather.host

import org.json.JSONObject

class NavigationModule(
    private val sendAction: (String, String?) -> Unit
) : FeatherModule {
    override val name: String = "feather-navigation"
    override val version: String = "1.0.0"

    override fun canHandleLegacyCommand(command: String): Boolean {
        return command.startsWith("Navigation/")
    }

    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean {
        return when {
            command.startsWith("Navigation/navigate=") -> {
                sendAction("__navigate", command.removePrefix("Navigation/navigate="))
                true
            }
            command == "Navigation/goBack" -> {
                sendAction("__goBack", null)
                true
            }
            else -> false
        }
    }

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        return when (method) {
            "navigate" -> {
                sendAction("__navigate", payload.optString("route"))
                true
            }
            "goBack" -> {
                sendAction("__goBack", null)
                true
            }
            "replace" -> {
                sendAction("__replace", payload.optString("route"))
                true
            }
            "reset" -> {
                sendAction("__reset", payload.optString("route"))
                true
            }
            else -> false
        }
    }
}
