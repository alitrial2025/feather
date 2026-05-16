package dev.feather.sdk

/**
 * A lightweight MessagePack encoder for the Feather SDK.
 * Supports: null, boolean, int, float, string, map, and array.
 */
class MessagePackEncoder {
    private val buffer = mutableListOf<Byte>()

    fun encode(value: Any?): ByteArray {
        buffer.clear()
        writeValue(value)
        return buffer.toByteArray()
    }

    private fun writeValue(value: Any?) {
        when (value) {
            null -> buffer.add(0xc0.toByte())
            is Boolean -> buffer.add(if (value) 0xc3.toByte() else 0xc2.toByte())
            is Int -> writeInt(value.toLong())
            is Long -> writeInt(value)
            is Float -> writeFloat(value.toDouble())
            is Double -> writeFloat(value)
            is String -> writeString(value)
            is List<*> -> writeArray(value)
            is Map<*, *> -> writeMap(value)
            else -> writeString(value.toString())
        }
    }

    private fun writeInt(value: Long) {
        when {
            value in 0..127 -> buffer.add(value.toByte())
            value in -31..-1 -> buffer.add((0xe0 or value.toInt()).toByte())
            value in Short.MIN_VALUE..Short.MAX_VALUE -> {
                buffer.add(0xd1.toByte())
                buffer.add((value shr 8).toByte())
                buffer.add(value.toByte())
            }
            value in Int.MIN_VALUE..Int.MAX_VALUE -> {
                buffer.add(0xd2.toByte())
                buffer.add((value shr 24).toByte())
                buffer.add((value shr 16).toByte())
                buffer.add((value shr 8).toByte())
                buffer.add(value.toByte())
            }
            else -> {
                buffer.add(0xd3.toByte())
                for (i in 7 downTo 0) {
                    buffer.add((value shr (i * 8)).toByte())
                }
            }
        }
    }

    private fun writeFloat(value: Double) {
        buffer.add(0xcb.toByte())
        val bits = value.toBits()
        for (i in 7 downTo 0) {
            buffer.add((bits shr (i * 8)).toByte())
        }
    }

    private fun writeString(value: String) {
        val bytes = value.encodeToByteArray()
        val len = bytes.size
        when {
            len < 32 -> buffer.add((0xa0 or len).toByte())
            len < 256 -> {
                buffer.add(0xd9.toByte())
                buffer.add(len.toByte())
            }
            len < 65536 -> {
                buffer.add(0xda.toByte())
                buffer.add((len shr 8).toByte())
                buffer.add(len.toByte())
            }
            else -> {
                buffer.add(0xdb.toByte())
                buffer.add((len shr 24).toByte())
                buffer.add((len shr 16).toByte())
                buffer.add((len shr 8).toByte())
                buffer.add(len.toByte())
            }
        }
        for (b in bytes) buffer.add(b)
    }

    private fun writeArray(value: List<*>) {
        val len = value.size
        when {
            len < 16 -> buffer.add((0x90 or len).toByte())
            len < 65536 -> {
                buffer.add(0xdc.toByte())
                buffer.add((len shr 8).toByte())
                buffer.add(len.toByte())
            }
            else -> {
                buffer.add(0xdd.toByte())
                buffer.add((len shr 24).toByte())
                buffer.add((len shr 16).toByte())
                buffer.add((len shr 8).toByte())
                buffer.add(len.toByte())
            }
        }
        for (item in value) writeValue(item)
    }

    private fun writeMap(value: Map<*, *>) {
        val len = value.size
        when {
            len < 16 -> buffer.add((0x80 or len).toByte())
            len < 65536 -> {
                buffer.add(0xde.toByte())
                buffer.add((len shr 8).toByte())
                buffer.add(len.toByte())
            }
            else -> {
                buffer.add(0xdf.toByte())
                buffer.add((len shr 24).toByte())
                buffer.add((len shr 16).toByte())
                buffer.add((len shr 8).toByte())
                buffer.add(len.toByte())
            }
        }
        for ((k, v) in value) {
            writeValue(k)
            writeValue(v)
        }
    }
}
