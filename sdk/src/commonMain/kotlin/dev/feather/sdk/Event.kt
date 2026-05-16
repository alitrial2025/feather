package dev.feather.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Global Event Bus for native platform notifications.
 */
object Event {
    private val listeners = mutableMapOf<String, MutableList<(String) -> Unit>>()

    /** Subscribe to a native event (e.g. "onResume", "locationChanged"). */
    fun subscribe(name: String, listener: (String) -> Unit) {
        listeners.getOrPut(name) { mutableListOf() }.add(listener)
    }

    /** Unsubscribe from an event. */
    fun unsubscribe(name: String, listener: (String) -> Unit) {
        listeners[name]?.remove(listener)
    }

    /** 
     * Internal: Dispatches events from the Core.
     */
    internal fun dispatchInternal(bytes: ByteArray) {
        try {
            val json = bytes.decodeToString()
            if (json.startsWith("[")) {
                val events = Json.decodeFromString<List<NativeEventData>>(json)
                for (event in events) {
                    listeners[event.name]?.forEach { it(event.payload) }
                }
            }
        } catch (e: Exception) {
            // Ignore malformed event packets
        }
    }
}

@Serializable
internal data class NativeEventData(val name: String, val payload: String)

/**
 * Helper for reacting to app lifecycle events.
 */
object Lifecycle {
    fun onResume(block: () -> Unit) = Event.subscribe("onResume") { block() }
    fun onPause(block: () -> Unit) = Event.subscribe("onPause") { block() }
    fun onStop(block: () -> Unit) = Event.subscribe("onStop") { block() }
}
