package com.example.mydeskrobot.memory.unified

import kotlin.math.sqrt

object VectorMath {

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom <= 0f) return 0f
        return (dot / denom).coerceIn(-1f, 1f)
    }

    fun normalize(values: FloatArray): FloatArray {
        var norm = 0f
        for (v in values) norm += v * v
        val denom = sqrt(norm)
        if (denom <= 0f) return values.copyOf()
        return FloatArray(values.size) { i -> values[i] / denom }
    }
}
