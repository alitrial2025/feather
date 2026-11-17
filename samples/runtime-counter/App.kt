package sample

import dev.feather.sdk.*

class Counter : FeatherApp() {
    override fun onCreate() {
        if (getState("count") == null) setState("count", "0")
    }

    override fun render(): Widget = Column(
        Text("Kotlin counter: ${getState("count") ?: "0"}"),
        Button(text = "Increment", action = "increment"),
        Button(text = "Load data", action = Network.get("https://example.com/", "response")),
        Text("Response: ${getState("response") ?: "not loaded"}")
    )

    override fun handleAction(action: String, payload: String?) {
        if (action == "increment") {
            val count = getState("count")?.toIntOrNull() ?: 0
            setState("count", (count + 1).toString())
        }
    }
}

fun main() { installApp(Counter()) }
