package dev.feather.sdk

import kotlin.wasm.unsafe.*

/**
 * Executes a block of code with a memory allocator, allowing Kotlin strings
 * to be temporarily written to WASM linear memory and passed to the Rust host.
 */
@OptIn(UnsafeWasmMemoryApi::class)
internal fun withWasmString(string: String, block: (ptr: Int, len: Int) -> Unit) {
    val bytes = string.encodeToByteArray()
    withScopedMemoryAllocator { allocator ->
        val ptr = allocator.allocate(bytes.size)
        // Copy bytes to linear memory
        for (i in bytes.indices) {
            val bytePtr = Pointer(ptr.address.toInt() + i)
            Pointer.storeByte(bytePtr, bytes[i])
        }
        block(ptr.address.toInt(), bytes.size)
    }
}
