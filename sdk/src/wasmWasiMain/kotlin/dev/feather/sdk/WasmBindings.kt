package dev.feather.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.wasm.unsafe.*

// ─── WASM ↔ Rust Host Function Imports ────────────────────────────────────────
//
// These are the functions the Rust `wasmi` engine exposes to the WASM sandbox.
// Kotlin/Wasm calls them to communicate with the native platform.

/** Log a message to the Rust CLI terminal. */
@WasmImport("env", "console_log")
internal external fun wasmConsoleLog(ptr: Int, len: Int)

/** Push a key/value pair into the shared AppRuntime state map. */
@WasmImport("env", "update_state")
internal external fun wasmUpdateState(keyPtr: Int, keyLen: Int, valPtr: Int, valLen: Int)

/** Push the full JSON UI tree to the Rust host for rendering. */
@WasmImport("env", "set_render_tree")
internal external fun wasmSetRenderTree(ptr: Int, len: Int)

@WasmImport("env", "get_action_payload")
internal external fun wasmGetActionPayload(ptr: Int): Int

/** Make an HTTP GET request through the Rust host (reqwest). Returns response length. */
@WasmImport("env", "fetch_network")
internal external fun wasmFetchNetwork(urlPtr: Int, urlLen: Int, resultPtr: Int): Int

/** Read a state value by key from the shared AppRuntime state map. Returns value length. */
@WasmImport("env", "get_state")
internal external fun wasmGetState(keyPtr: Int, keyLen: Int, resultPtr: Int): Int

/** Call a native module through the Rust host bridge (Phase 16). */
@WasmImport("env", "call_module")
internal external fun wasmCallModule(
    namePtr: Int, nameLen: Int, 
    actionPtr: Int, actionLen: Int, 
    payloadPtr: Int, payloadLen: Int,
    cbKeyPtr: Int, cbKeyLen: Int
): Int

// ─── Memory Helpers ──────────────────────────────────────────────────────────
// Memory management is now handled via Memory.kt (withWasmString)

// ─── Public SDK Helpers ──────────────────────────────────────────────────────

/** Log a message to the Feather CLI console. */
fun console(message: String) {
    withWasmString(message) { ptr, len ->
        wasmConsoleLog(ptr, len)
    }
}

/** Update a state key in the shared runtime. Triggers a re-render. */
fun setState(key: String, value: String) {
    withWasmString(key) { keyPtr, keyLen ->
        withWasmString(value) { valPtr, valLen ->
            wasmUpdateState(keyPtr, keyLen, valPtr, valLen)
        }
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
fun getState(key: String): String? {
    var result: String? = null
    withWasmString(key) { keyPtr, keyLen ->
        withScopedMemoryAllocator { allocator ->
            val resultPtr = allocator.allocate(1024 * 1024)
            val len = wasmGetState(keyPtr, keyLen, resultPtr.address.toInt())
            if (len > 0) {
                val bytes = ByteArray(len)
                for (i in 0 until len) {
                    bytes[i] = Pointer.loadByte(Pointer(resultPtr.address.toInt() + i))
                }
                result = bytes.decodeToString()
            }
        }
    }
    return result
}

@OptIn(UnsafeWasmMemoryApi::class)
fun fetchNetwork(url: String): String {
    var response = ""
    withWasmString(url) { urlPtr, urlLen ->
        withScopedMemoryAllocator { allocator ->
            val resultPtr = allocator.allocate(1024 * 1024 * 5) // 5MB buffer
            val len = wasmFetchNetwork(urlPtr, urlLen, resultPtr.address.toInt())
            if (len > 0) {
                val bytes = ByteArray(len)
                for (i in 0 until len) {
                    bytes[i] = Pointer.loadByte(Pointer(resultPtr.address.toInt() + i))
                }
                response = bytes.decodeToString()
            }
        }
    }
    return response
}

/** Call a native module and store the result in a state key. */
fun callModule(moduleName: String, action: String, payload: String = "{}", stateKey: String) {
    withWasmString(moduleName) { namePtr, nameLen ->
        withWasmString(action) { actPtr, actLen ->
            withWasmString(payload) { payPtr, payLen ->
                withWasmString(stateKey) { cbPtr, cbLen ->
                    wasmCallModule(namePtr, nameLen, actPtr, actLen, payPtr, payLen, cbPtr, cbLen)
                }
            }
        }
    }
}

// ─── WASM Exports (called BY the Rust host) ──────────────────────────────────

/** Global app instance. Set by the user's main() function. */
var featherApp: FeatherApp? = null

/**
 * Called by the Rust host to render the current UI.
 * Executes the app's render() function, serializes the Widget tree to JSON,
 * and pushes it to the host via set_render_tree.
 */
@WasmExport
fun render_ui() {
    val app = featherApp ?: return
    val root = app.render()
    val json = Json.encodeToString<Widget>(root)
    withWasmString(json) { ptr, len ->
        wasmSetRenderTree(ptr, len)
    }
}

/**
 * Called by the Rust host when a user interaction occurs (button tap, input change, etc).
 * Routes the action string to the app's handleAction method.
 */
@OptIn(UnsafeWasmMemoryApi::class)
@WasmExport
fun handle_action() {
    withScopedMemoryAllocator { allocator ->
        val ptr = allocator.allocate(1024 * 128) // 128KB buffer for payload
        val len = wasmGetActionPayload(ptr.address.toInt())
        if (len > 0) {
            val bytes = ByteArray(len)
            for (i in 0 until len) {
                bytes[i] = Pointer.loadByte(Pointer(ptr.address.toInt() + i))
            }
            val actionStr = bytes.decodeToString()
            featherApp?.handleAction(actionStr, null)
        }
    }
}
