import java.io.File
import uniffi.feather_core.AppRuntime

fun main(args: Array<String>) {
    val runtime = AppRuntime()
    try {
        runtime.loadJavascript(File(args[0]).readText())
        check(runtime.hasJavascript())
        check(runtime.renderJavascriptDocument().contains("Kotlin counter: 0"))
        runtime.applyActionEvent("""{"type":"action","action":"increment"}""")
        check(runtime.renderJavascriptDocument().contains("Kotlin counter: 1"))
        runtime.pushNativeEvent("onResume", "{}")
        check(runtime.renderJavascriptDocument().contains("Kotlin counter: 1"))
        println("PASS: Kotlin/JNA bindings execute compiled Kotlin through the native Rust runtime")
    } finally {
        runtime.destroy()
    }
}
