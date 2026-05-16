package dev.feather.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class NetworkModule : FeatherModule {
    override val name: String = "feather-network"
    override val version: String = "1.0.0"

    override fun canHandleLegacyCommand(command: String): Boolean {
        return command.startsWith("Network/")
    }

    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean {
        val method = when {
            command.startsWith("Network/get=") -> "GET"
            command.startsWith("Network/post=") -> "POST"
            command.startsWith("Network/put=") -> "PUT"
            command.startsWith("Network/delete=") -> "DELETE"
            else -> return false
        }
        val payload = parseCommandJson(command.substringAfter('='))
        performRequest(method, payload.optString("url"), payload.optString("body", ""), payload.optString("stateKey"), context)
        return true
    }

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        val httpMethod = when (method.lowercase()) {
            "get" -> "GET"
            "post" -> "POST"
            "put" -> "PUT"
            "delete" -> "DELETE"
            else -> return false
        }
        performRequest(httpMethod, payload.optString("url"), payload.optString("body", ""), null, context)
        return true
    }

    private fun performRequest(method: String, url: String, bodyText: String, legacyStateKey: String?, context: FeatherModuleContext) {
        if (url.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                val body = when (method) {
                    "POST", "PUT" -> bodyText.toRequestBody("application/json; charset=utf-8".toMediaType())
                    else -> null
                }
                val httpRequest = Request.Builder()
                    .url(url)
                    .method(method, body)
                    .build()
                context.httpClient.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("HTTP ${response.code}: $responseBody")
                    responseBody
                }
            }
            CoroutineScope(Dispatchers.Main).launch {
                result
                    .onSuccess { context.sendStateAssignment(legacyStateKey ?: "", it) }
                    .onFailure {
                        val key = if (legacyStateKey != null) "${legacyStateKey}_error" else "error"
                        context.sendStateAssignment(key, it.message ?: "Network request failed")
                    }
            }
        }
    }
}

internal fun parseCommandJson(payload: String): JSONObject {
    return runCatching { JSONObject(payload) }.getOrElse { JSONObject() }
}
