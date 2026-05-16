package dev.feather.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.wasm.unsafe.*

// ─── WASM ↔ Rust Host Function Imports ────────────────────────────────────────

/** Log a message to the Rust CLI terminal. */
@WasmImport("env", "console_log")
internal external fun wasmConsoleLog(ptr: Int, len: Int)

/** Push a key/value pair into the shared AppRuntime state map. */
@WasmImport("env", "update_state")
internal external fun wasmUpdateState(keyPtr: Int, keyLen: Int, valPtr: Int, valLen: Int)

/** Push the full JSON UI tree to the Rust host for rendering. */
@WasmImport("env", "set_render_tree")
internal external fun wasmSetRenderTree(ptr: Int, len: Int)

/** Push the full binary (MessagePack) UI tree to the Rust host for rendering. */
@WasmImport("env", "set_render_tree_bin")
internal external fun wasmSetRenderTreeBin(ptr: Int, len: Int)

@WasmImport("env", "get_action_payload")
internal external fun wasmGetActionPayload(ptr: Int): Int

/** Make an HTTP GET request through the Rust host (reqwest). Returns response length. */
@WasmImport("env", "fetch_network")
internal external fun wasmFetchNetwork(urlPtr: Int, urlLen: Int, resultPtr: Int): Int

/** Read a state value by key from the shared AppRuntime state map. Returns value length. */
@WasmImport("env", "get_state")
internal external fun wasmGetState(keyPtr: Int, keyLen: Int, resultPtr: Int): Int

@WasmImport("env", "msgpack_update_state")
internal external fun wasmMsgpackUpdateState(ptr: Int, len: Int): Int

@WasmImport("env", "msgpack_get_state")
internal external fun wasmMsgpackGetState(keyPtr: Int, keyLen: Int, resultPtr: Int): Int

@WasmImport("env", "msgpack_call_module")
internal external fun wasmMsgpackCallModule(ptr: Int, len: Int): Int

@WasmImport("env", "drain_native_events")
internal external fun wasmDrainNativeEvents(resultPtr: Int): Int

/** Call a native module through the Rust host bridge. */
@WasmImport("env", "call_module")
internal external fun wasmCallModule(
    namePtr: Int, nameLen: Int, 
    actionPtr: Int, action_len: Int, 
    payloadPtr: Int, payloadLen: Int,
    cbKeyPtr: Int, cbKeyLen: Int
): Int

// ─── Public SDK Helpers ──────────────────────────────────────────────────────

fun console(message: String) {
    withWasmString(message) { ptr, len ->
        wasmConsoleLog(ptr, len)
    }
}

fun setState(key: String, value: String) {
    withWasmString(key) { keyPtr, keyLen ->
        withWasmString(value) { valPtr, valLen ->
            wasmUpdateState(keyPtr, keyLen, valPtr, valLen)
        }
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
fun setStates(updates: Map<String, Any?>) {
    val encoder = MessagePackEncoder()
    val bytes = encoder.encode(updates)
    withScopedMemoryAllocator { allocator ->
        val ptr = allocator.allocate(bytes.size)
        for (i in bytes.indices) {
            Pointer((ptr.address.toUInt() + i.toUInt())).storeByte(bytes[i])
        }
        wasmMsgpackUpdateState(ptr.address.toInt(), bytes.size)
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
fun callModuleMsgpack(module: String, method: String, payload: Any? = null, stateKey: String) {
    val encoder = MessagePackEncoder()
    val callData = mapOf(
        "module" to module,
        "method" to method,
        "payload" to payload,
        "resultStateKey" to stateKey
    )
    val bytes = encoder.encode(callData)
    withScopedMemoryAllocator { allocator ->
        val ptr = allocator.allocate(bytes.size)
        for (i in bytes.indices) {
            Pointer((ptr.address.toUInt() + i.toUInt())).storeByte(bytes[i])
        }
        wasmMsgpackCallModule(ptr.address.toInt(), bytes.size)
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
                    bytes[i] = Pointer((resultPtr.address.toUInt() + i.toUInt())).loadByte()
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
            val resultPtr = allocator.allocate(1024 * 1024 * 5)
            val len = wasmFetchNetwork(urlPtr, urlLen, resultPtr.address.toInt())
            if (len > 0) {
                val bytes = ByteArray(len)
                for (i in 0 until len) {
                    bytes[i] = Pointer((resultPtr.address.toUInt() + i.toUInt())).loadByte()
                }
                response = bytes.decodeToString()
            }
        }
    }
    return response
}

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

// ─── WASM Exports ───────────────────────────────────────────────────────────

var featherApp: FeatherApp? = null

@WasmExport
fun render_ui() {
    val app = featherApp ?: return
    val root = app.render()
    val json = Json.encodeToString<Widget>(root)
    withWasmString(json) { ptr, len ->
        wasmSetRenderTree(ptr, len)
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
@WasmExport
fun render_ui_bin() {
    val app = featherApp ?: return
    val root = app.render()
    val encoder = MessagePackEncoder()
    val bytes = encoder.encode(root)
    withScopedMemoryAllocator { allocator ->
        val ptr = allocator.allocate(bytes.size)
        for (i in bytes.indices) {
            Pointer((ptr.address.toUInt() + i.toUInt())).storeByte(bytes[i])
        }
        wasmSetRenderTreeBin(ptr.address.toInt(), bytes.size)
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
@WasmExport
fun handle_action() {
    withScopedMemoryAllocator { allocator ->
        val ptr = allocator.allocate(1024 * 128)
        val len = wasmGetActionPayload(ptr.address.toInt())
        if (len > 0) {
            val bytes = ByteArray(len)
            for (i in 0 until len) {
                bytes[i] = Pointer((ptr.address.toUInt() + i.toUInt())).loadByte()
            }
            val actionStr = bytes.decodeToString()
            featherApp?.handleAction(actionStr, null)
        }
    }

    // Process Async Events
    withScopedMemoryAllocator { allocator ->
        val resultPtr = allocator.allocate(1024 * 256)
        val len = wasmDrainNativeEvents(resultPtr.address.toInt())
        if (len > 0) {
            val bytes = ByteArray(len)
            for (i in 0 until len) {
                bytes[i] = Pointer((resultPtr.address.toUInt() + i.toUInt())).loadByte()
            }
            Event.dispatchInternal(bytes)
        }
    }
}
