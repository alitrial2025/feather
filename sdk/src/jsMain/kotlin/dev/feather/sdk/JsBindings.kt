@file:Suppress("UNUSED_PARAMETER")
package dev.feather.sdk

import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Install an application into Feather's persistent interpreter. */
fun installApp(app: FeatherApp) {
    val render: () -> String = { Json.encodeToString<Widget>(app.render()) }
    val action: (String, String?) -> Unit = { name, payload -> app.handleAction(name, payload) }
    val events: (String) -> Unit = { Event.dispatchInternal(it.encodeToByteArray()) }
    js("globalThis.__featherRender = render; globalThis.__featherAction = action; globalThis.__featherEvents = events")
    app.onCreate()
}

fun getState(key: String): String? = js("globalThis.__featherGetState(key)") as String?
fun setState(key: String, value: String) { js("globalThis.__featherSetState(key, value)") }

/** Store text verbatim, including text that looks like a JSON value. */
fun setStateText(key: String, value: String) = setState(key, Json.encodeToString(value))

/** Store a serializable object or collection in the runtime state. */
inline fun <reified T> setStateJson(key: String, value: T) = setState(key, Json.encodeToString(value))

/** Read an object or collection, returning null when absent or incompatible. */
inline fun <reified T> getStateJson(key: String): T? {
    val value = getState(key) ?: return null
    return try {
        Json.decodeFromString<T>(value)
    } catch (_: SerializationException) {
        null
    }
}

/** Read text from an action sent by a native widget or DevTools. */
fun actionText(payload: String?): String {
    if (payload == null) return ""
    return try {
        Json.decodeFromString<String>(payload)
    } catch (_: SerializationException) {
        payload
    }
}

fun console(message: String) { js("globalThis.__featherLog(message)") }

fun callModuleMsgpack(module: String, method: String, payload: Any? = null, stateKey: String) {
    val bytes = MessagePackEncoder().encode(mapOf(
        "module" to module, "method" to method, "payload" to payload, "resultStateKey" to stateKey
    ))
    val encoded = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    js("globalThis.__featherModule(encoded)")
}
