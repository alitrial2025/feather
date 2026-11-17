@file:Suppress("UNUSED_PARAMETER")
package dev.feather.sdk

import kotlinx.serialization.encodeToString
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
fun console(message: String) { js("globalThis.__featherLog(message)") }

fun callModuleMsgpack(module: String, method: String, payload: Any? = null, stateKey: String) {
    val bytes = MessagePackEncoder().encode(mapOf(
        "module" to module, "method" to method, "payload" to payload, "resultStateKey" to stateKey
    ))
    val encoded = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    js("globalThis.__featherModule(encoded)")
}
