package dev.feather.host

import org.json.JSONObject

class AuthModule : FeatherModule {
    override val name: String = "feather-auth"
    override val version: String = "1.0.0"
    override fun canHandleLegacyCommand(command: String): Boolean = false
    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean = false

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        return when (method) {
            "biometricPrompt" -> {
                context.sendStateAssignment("", "{\"success\": true}")
                true
            }
            "storeToken" -> {
                true
            }
            "getToken" -> {
                context.sendStateAssignment("", "{}")
                true
            }
            else -> false
        }
    }
}
