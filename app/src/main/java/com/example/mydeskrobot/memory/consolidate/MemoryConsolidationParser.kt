package com.example.mydeskrobot.memory.consolidate

import com.example.mydeskrobot.memory.db.MemoryCategory

data class ConsolidatedMemoryLine(
    val category: MemoryCategory,
    val value: String,
)

object MemoryConsolidationParser {

    private val CATEGORY_LINE = Regex(
        """^\s*\(\s*(IDENTITY|PREFERENCE|ROUTINE|FACT)\s*\)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Parses LLM output lines in the form `(ROUTINE) L'utente lavora ...`.
     */
    fun parseLines(raw: String): List<ConsolidatedMemoryLine> {
        val body = stripCodeFence(raw)
        val result = mutableListOf<ConsolidatedMemoryLine>()
        for (line in body.lines()) {
            val trimmed = normalizeLine(line)
            if (trimmed.isBlank()) continue
            val match = CATEGORY_LINE.matchEntire(trimmed) ?: continue
            val category = parseCategory(match.groupValues[1]) ?: continue
            val value = match.groupValues[2].trim()
            if (value.length < 8) continue
            result += ConsolidatedMemoryLine(category = category, value = value)
        }
        return result
    }

    fun stripCodeFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .trim()
            .substringBeforeLast("```")
            .trim()
    }

    private fun normalizeLine(line: String): String {
        var value = line.trim()
        if (value == "[" || value == "]") return ""
        value = value.trimStart('[', ' ')
        value = value.trimEnd(',', ' ')
        value = value.trim().trim('"').trim()
        return value
    }

    private fun parseCategory(raw: String): MemoryCategory? =
        runCatching { MemoryCategory.valueOf(raw.trim().uppercase()) }.getOrNull()
}
