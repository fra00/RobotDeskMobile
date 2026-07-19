package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.db.MemoryCategory

/**
 * Exact-value duplicate detection for user-facing memory writes (no semantic merge).
 */
object MemoryExactMatch {

    fun normalize(value: String): String =
        MemoryDuplicateDetector.normalizeForDedup(value)

    fun isExactDuplicate(
        category: MemoryCategory,
        valueA: String,
        valueB: String,
        categoryB: MemoryCategory = category,
    ): Boolean {
        if (category != categoryB) return false
        val left = normalize(valueA)
        val right = normalize(valueB)
        return left.isNotBlank() && left == right
    }
}
