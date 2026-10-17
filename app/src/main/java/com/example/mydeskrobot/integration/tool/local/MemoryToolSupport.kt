package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.data.predictivity.HabitSlotCodec
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity

internal object MemoryToolSupport {
    /** Marks facts saved explicitly via LLM tool (not conversation log extraction). */
    const val SOURCE_MESSAGE_LLM_TOOL: Long = -1L

    const val MEMORY_ROLE_EXPECTED_HABIT = "expected_habit"
    const val MEMORY_ROLE_USER_FACT = "user_fact"
    const val MEMORY_ROLE_ROBOT_NOTE = "robot_note"

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

    fun parsePinned(raw: Any?): Boolean =
        when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase()) {
                "true", "1", "yes", "si", "sì" -> true
                else -> false
            }
            else -> false
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
            "memory_role" to memoryRoleForLegacy(entity.category),
        )
        entity.expiresAt?.let { base["expires_at"] = it }
        return base
    }

    fun documentToMap(entity: MemoryDocumentEntity): Map<String, Any?> {
        val slot = HabitSlotCodec.decode(entity.value)
        val displayValue = if (slot != null) {
            HabitSlotCodec.formatForPrompt(slot)
        } else {
            entity.value
        }
        val base = mutableMapOf<String, Any?>(
            "id" to entity.id,
            "category" to entity.category,
            "value" to displayValue,
            "confidence" to entity.confidence,
            "updated_at" to entity.updatedAt,
            "memory_role" to memoryRoleForDocument(entity, isHabitSlot = slot != null),
        )
        entity.expiresAt?.let { base["expires_at"] = it }
        return base
    }

    private fun memoryRoleForDocument(
        entity: MemoryDocumentEntity,
        isHabitSlot: Boolean,
    ): String {
        if (isHabitSlot) return MEMORY_ROLE_EXPECTED_HABIT
        val category = entity.category?.uppercase().orEmpty()
        return when {
            entity.kind == MemoryDocumentKind.AUTONOMY.name &&
                category == MemoryCategory.PATTERN.name -> MEMORY_ROLE_EXPECTED_HABIT
            entity.kind == MemoryDocumentKind.AUTONOMY.name -> MEMORY_ROLE_ROBOT_NOTE
            category in setOf(
                MemoryCategory.OBSERVATION.name,
                MemoryCategory.INTENT.name,
                MemoryCategory.PATTERN.name,
            ) -> MEMORY_ROLE_ROBOT_NOTE
            else -> MEMORY_ROLE_USER_FACT
        }
    }

    private fun memoryRoleForLegacy(category: MemoryCategory): String =
        when (category) {
            MemoryCategory.OBSERVATION,
            MemoryCategory.INTENT,
            MemoryCategory.PATTERN,
            -> MEMORY_ROLE_ROBOT_NOTE
            else -> MEMORY_ROLE_USER_FACT
        }
}
