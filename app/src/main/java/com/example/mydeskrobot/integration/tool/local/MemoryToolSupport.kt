package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity

internal object MemoryToolSupport {
    /** Marks facts saved explicitly via LLM tool (not conversation log extraction). */
    const val SOURCE_MESSAGE_LLM_TOOL: Long = -1L

    fun parseCategory(raw: Any?): MemoryCategory? {
        val normalized = when (raw) {
            null -> return null
            is String -> raw.trim().uppercase()
            else -> raw.toString().trim().uppercase()
        }
        if (normalized.isBlank()) return null
        return runCatching { MemoryCategory.valueOf(normalized) }.getOrNull()
    }

    fun parseConfidence(raw: Any?): Float =
        when (raw) {
            is Number -> raw.toFloat()
            is String -> raw.toFloatOrNull()
            else -> null
        }?.coerceIn(0f, 1f) ?: 0.85f

    fun parseLimit(raw: Any?, default: Int = 20, max: Int = 50): Int {
        val value = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: default
        return value.coerceIn(1, max)
    }

    fun parseTtlDays(raw: Any?): Int? {
        val value = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: return null
        return value.coerceAtLeast(1)
    }

    fun legacyEntityToMap(entity: MemoryItemEntity): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>(
            "id" to entity.id,
            "category" to entity.category.name,
            "value" to entity.value,
            "confidence" to entity.confidence,
            "updated_at" to entity.updatedAt,
        )
        entity.expiresAt?.let { base["expires_at"] = it }
        return base
    }

    fun documentToMap(entity: MemoryDocumentEntity): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>(
            "id" to entity.id,
            "category" to entity.category,
            "value" to entity.value,
            "confidence" to entity.confidence,
            "updated_at" to entity.updatedAt,
        )
        entity.expiresAt?.let { base["expires_at"] = it }
        return base
    }
}
