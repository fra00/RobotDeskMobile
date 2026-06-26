package com.example.mydeskrobot.memory.unified

import java.nio.ByteBuffer
import java.nio.ByteOrder

object EmbeddingCodec {

    fun encode(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun decode(bytes: ByteArray?): FloatArray? {
        if (bytes == null || bytes.isEmpty() || bytes.size % 4 != 0) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(bytes.size / 4)
        for (i in result.indices) {
            result[i] = buffer.getFloat(i * 4)
        }
        return result
    }
}
