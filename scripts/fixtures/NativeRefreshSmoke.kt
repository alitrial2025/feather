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
    reload()
    check(runtime.renderJavascriptDocument().contains("Kotlin counter: 0"))
    runtime.applyActionEvent("""{"action":"increment"}""")
    check(runtime.renderJavascriptDocument().contains("Kotlin counter: 1"))
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
                        check(runtime.renderJavascriptDocument().contains("Updated Kotlin: 1"))
                        runtime.applyActionEvent("""{"action":"increment"}""")
                        check(runtime.renderJavascriptDocument().contains("Updated Kotlin: 3"))
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
        source.writeText(source.readText().replace("Kotlin counter:", "Updated Kotlin:").replace("count + 1", "count + 2"))
        check(done.await(180, TimeUnit.SECONDS)) { "No native code refresh notification" }
        failure.get()?.let { throw AssertionError("Native refresh failed", it) }
        println("PASS: signed code refresh through Kotlin/JNA, independent client state retained, new logic executes locally")
    } finally {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
        runtime.destroy()
    }
}
