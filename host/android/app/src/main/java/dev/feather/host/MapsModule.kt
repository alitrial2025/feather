package dev.feather.host

import org.json.JSONObject

class MapsModule : FeatherModule {
    override val name: String = "feather-maps"
    override val version: String = "1.0.0"
    override fun canHandleLegacyCommand(command: String): Boolean = false
    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean = false

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        return true
    }
}
