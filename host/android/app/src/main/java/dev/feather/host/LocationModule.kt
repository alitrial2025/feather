package dev.feather.host

import org.json.JSONObject

class LocationModule : FeatherModule {
    override val name: String = "feather-location"
    override val version: String = "1.0.0"
    override fun canHandleLegacyCommand(command: String): Boolean = false
    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean = false

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        return when (method) {
            "getCurrentLocation" -> {
                // Return a mock/placeholder location for now
                val location = JSONObject().apply {
                    put("latitude", 37.7749)
                    put("longitude", -122.4194)
                    put("accuracy", 10.0)
                }
                context.sendStateAssignment("", location.toString())
                true
            }
            else -> false
        }
    }
}
