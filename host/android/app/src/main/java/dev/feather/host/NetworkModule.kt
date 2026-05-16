package dev.feather.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray

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
            command.startsWith("Network/patch=") -> "PATCH"
            command.startsWith("Network/delete=") -> "DELETE"
            else -> return false
        }
        val payload = parseCommandJson(command.substringAfter('='))
        performRequest(method, payload, payload.optString("stateKey"), context)
        return true
    }

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        val httpMethod = when (method.lowercase()) {
            "get" -> "GET"
            "post" -> "POST"
            "put" -> "PUT"
            "patch" -> "PATCH"
            "delete" -> "DELETE"
            else -> return false
        }
        performRequest(httpMethod, payload, null, context)
        return true
    }

    private fun performRequest(method: String, payload: JSONObject, legacyStateKey: String?, context: FeatherModuleContext) {
        val url = payload.optString("url")
        if (url.isBlank()) return

        val headersObj = payload.optJSONObject("headers")
        val headersMap = mutableMapOf<String, String>()
        headersObj?.keys()?.forEach { key ->
            headersMap[key] = headersObj.getString(key)
        }

        val bodyType = payload.optString("bodyType")
        val bodyVal = payload.opt("body")
        val bodyText = when (bodyVal) {
            is JSONObject, is JSONArray -> bodyVal.toString()
            null -> ""
            else -> bodyVal.toString()
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val body = when (method) {
                    "POST", "PUT", "PATCH" -> {
                        if (bodyType == "form" && bodyVal is JSONObject) {
                            val formBuilder = okhttp3.FormBody.Builder()
                            bodyVal.keys().forEach { key ->
                                formBuilder.add(key, bodyVal.getString(key))
                            }
                            formBuilder.build()
                        } else {
                            bodyText.toRequestBody("application/json; charset=utf-8".toMediaType())
                        }
                    }
                    "DELETE" -> if (bodyText.isNotEmpty()) bodyText.toRequestBody("application/json; charset=utf-8".toMediaType()) else null
                    else -> null
                }
                
                val httpRequest = Request.Builder()
                    .url(url)
                    .method(method, body)
                    .headers(headersMap.toHeaders())
                    .build()

                context.httpClient.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    
                    val responseHeaders = JSONObject()
                    response.headers.forEach { (name, value) ->
                        responseHeaders.put(name, value)
                    }

                    val resultObj = JSONObject().apply {
                        put("status", response.code)
                        put("headers", responseHeaders)
                        
                        // Try parsing body as JSON, fallback to raw string
                        val jsonBody = runCatching { JSONObject(responseBody) }.getOrNull()
                        val jsonArray = if (jsonBody == null) runCatching { JSONArray(responseBody) }.getOrNull() else null
                        
                        when {
                            jsonBody != null -> put("body", jsonBody)
                            jsonArray != null -> put("body", jsonArray)
                            else -> put("body", responseBody)
                        }
                    }

                    if (!response.isSuccessful) {
                        throw NetworkException(resultObj)
                    }
                    resultObj
                }
            }.onSuccess { resultObj ->
                CoroutineScope(Dispatchers.Main).launch {
                    context.sendStateAssignment(legacyStateKey ?: "", resultObj.toString())
                }
            }.onFailure { error ->
                CoroutineScope(Dispatchers.Main).launch {
                    val key = if (legacyStateKey != null) "${legacyStateKey}_error" else "error"
                    val errorObj = if (error is NetworkException) {
                        error.responseJson
                    } else {
                        JSONObject().apply {
                            put("code", "network_error")
                            put("message", error.message ?: "Network request failed")
                            put("recoverable", true)
                        }
                    }
                    context.sendStateAssignment(key, errorObj.toString())
                }
            }
        }
    }
}

class NetworkException(val responseJson: JSONObject) : Exception("Network request returned an error status")

internal fun parseCommandJson(payload: String): JSONObject {
    return runCatching { JSONObject(payload) }.getOrElse { JSONObject() }
}
