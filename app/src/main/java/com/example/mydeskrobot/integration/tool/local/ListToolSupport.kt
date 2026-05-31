package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.data.lists.db.ListItemEntity
import com.example.mydeskrobot.domain.list.ListItemType

internal object ListToolSupport {

    fun parseType(raw: Any?): ListItemType? {
        val normalized = when (raw) {
            null -> return null
            is String -> raw.trim().uppercase()
            else -> raw.toString().trim().uppercase()
        }
        if (normalized.isBlank()) return null
        return runCatching { ListItemType.valueOf(normalized) }.getOrNull()
    }

    fun parseLimit(raw: Any?, default: Int = 30, max: Int = 100): Int {
        val value = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: default
        return value.coerceIn(1, max)
    }

    fun parseChecked(raw: Any?): Boolean? {
        return when (raw) {
            null -> null
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase()) {
                "true", "1", "yes", "si", "sì" -> true
                "false", "0", "no" -> false
                else -> null
            }
            else -> null
        }
    }

    fun entityToMap(entity: ListItemEntity): Map<String, Any?> = mapOf(
        "id" to entity.id,
        "type" to entity.type.name.lowercase(),
        "text" to entity.text,
        "checked" to entity.checked,
        "created_at" to entity.createdAtMillis,
        "updated_at" to entity.updatedAtMillis,
    )
}
