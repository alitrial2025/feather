package dev.feather.host

import android.content.Context

class StorageModule : FeatherModule {
    override val name: String = "feather-storage"
    override val version: String = "1.0.0"

    override fun canHandleLegacyCommand(command: String): Boolean {
        return command.startsWith("Storage/")
    }

    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean {
        val preferences = context.appContext.getSharedPreferences("feather", Context.MODE_PRIVATE)
        return when {
            command.startsWith("Storage/set=") -> {
                val request = parseCommandJson(command.removePrefix("Storage/set="))
                val key = request.optString("key")
                if (key.isNotBlank()) {
                    preferences.edit()
                        .putString(key, request.optString("value"))
                        .apply()
                }
                true
            }
            command.startsWith("Storage/get=") -> {
                val request = parseCommandJson(command.removePrefix("Storage/get="))
                val key = request.optString("key")
                val stateKey = request.optString("stateKey")
                if (key.isNotBlank() && stateKey.isNotBlank()) {
                    val value = preferences.getString(key, "") ?: ""
                    context.sendStateAssignment(stateKey, value)
                }
                true
            }
            command.startsWith("Storage/remove=") -> {
                val key = command.removePrefix("Storage/remove=")
                preferences.edit().remove(key).apply()
                true
            }
            command == "Storage/clear" -> {
                preferences.edit().clear().apply()
                true
            }
            else -> false
        }
    }

    override fun call(method: String, payload: org.json.JSONObject, context: FeatherModuleContext): Boolean {
        val preferences = context.appContext.getSharedPreferences("feather", Context.MODE_PRIVATE)
        return when (method.lowercase()) {
            "set" -> {
                val key = payload.optString("key")
                if (key.isNotBlank()) {
                    preferences.edit()
                        .putString(key, payload.optString("value"))
                        .apply()
                }
                true
            }
            "get" -> {
                val key = payload.optString("key")
                if (key.isNotBlank()) {
                    val value = preferences.getString(key, "") ?: ""
                    context.sendStateAssignment("", value) // key is ignored by wrapper if resultStateKey was provided
                }
                true
            }
            "remove" -> {
                val key = payload.optString("key")
                if (key.isNotBlank()) {
                    preferences.edit().remove(key).apply()
                }
                true
            }
            "clear" -> {
                preferences.edit().clear().apply()
                true
            }
            else -> false
        }
    }
}
