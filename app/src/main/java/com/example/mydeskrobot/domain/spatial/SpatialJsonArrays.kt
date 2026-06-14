package com.example.mydeskrobot.domain.spatial

/**
 * Minimal JSON string array codec (JVM-safe, no Android org.json).
 */
object SpatialJsonArrays {

    private val STRING_ITEM_PATTERN = Regex(""""((?:\\.|[^"\\])*)"""")

    fun encode(items: Iterable<String>): String {
        val body = items
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = ",") { item ->
                "\"${item.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            }
        return "[$body]"
    }

    fun decode(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val trimmed = json.trim()
        if (!trimmed.startsWith('[')) return emptyList()
        return STRING_ITEM_PATTERN.findAll(trimmed)
            .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\").trim() }
            .filter { it.isNotBlank() }
            .toList()
    }
}
