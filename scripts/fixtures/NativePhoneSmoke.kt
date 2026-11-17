import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import uniffi.feather_core.AppRuntime

fun main(args: Array<String>) {
    val base = args[0]
    val key = File(args[1]).readText()
    val source = File(args[2])
    val runtime = AppRuntime()
    val http = HttpClient.newHttpClient()
    fun fetch(path: String): String {
        val response = http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200)
        return response.body()
    }
    fun reload() { runtime.loadSignedJavascript(fetch("/bundle/manifest"), fetch("/bundle/artifact/app.js"), key) }
    fun action(name: String, payload: String? = null) {
        runtime.applyActionEvent("""{"action":"$name","payload":${payload ?: "null"}}""")
    }
    fun document() = runtime.renderJavascriptDocument()
    reload()
    check(document().contains("WhatsApp") && document().contains("Amina Hassan"))
    action("filter:Groups")
    check(document().contains("Weekend plans") && !document().contains("Amina Hassan"))
    action("filter:All")
    action("open:amina")
    check(document().contains("Are we still meeting at 6?"))
    action("draft", "\"Hello from Feather\"")
    action("send")
    check(document().contains("Hello from Feather"))
    check(runtime.getState("draft:amina") == "\"\"")
    action("back")
    check(document().contains("Hello from Feather"))
    action("query", "\"David\"")
    check(document().contains("David Otieno") && !document().contains("Amina Hassan"))
    action("query", "\"\"")
    action("tab:updates")
    action("status-draft", "\"Building with Feather\"")
    action("post-status")
    check(document().contains("Building with Feather"))
    action("tab:chats")
    val done = CountDownLatch(1)
    val handled = AtomicBoolean(false)
    val failure = AtomicReference<Throwable?>()
    val socket = http.newWebSocketBuilder().buildAsync(URI.create(base.replace("http://", "ws://") + "/ws"), object : WebSocket.Listener {
        val fragments = StringBuilder()
        override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            fragments.append(data)
            if (last) {
                val message = fragments.toString()
                fragments.setLength(0)
                if (Regex("\"type\"\\s*:\\s*\"hot-reload-js\"").containsMatchIn(message) && handled.compareAndSet(false, true)) {
                    try {
                        reload()
                        check(document().contains("Feather Chat"))
                        check(document().contains("Hello from Feather"))
                        action("open:amina")
                        check(document().contains("Hello from Feather"))
                    } catch (error: Throwable) { failure.set(error) }
                    finally { done.countDown() }
                }
            }
            webSocket.request(1)
            return null
        }
        override fun onError(webSocket: WebSocket, error: Throwable) { failure.set(error); done.countDown() }
    }).join()
    try {
        source.writeText(source.readText().replace("\"WhatsApp\"", "\"Feather Chat\""))
        check(done.await(180, TimeUnit.SECONDS)) { "No signed code refresh notification" }
        failure.get()?.let { throw AssertionError("Native WhatsApp refresh failed", it) }
        println("PASS: native signed chat app, filters, search, messages, status, and live source refresh with client state retained")
    } finally {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
        runtime.destroy()
    }
}
