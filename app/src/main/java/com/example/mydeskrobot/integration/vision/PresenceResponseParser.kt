package com.example.mydeskrobot.integration.vision

import com.example.mydeskrobot.domain.vision.PresenceDetectionResult
import com.example.mydeskrobot.domain.vision.PresenceStatus

/**
 * Parses LLM JSON output for presence detection.
 * Kotlin-only for unit tests (no Moshi/Android).
 */
object PresenceResponseParser {

    private val PRESENCE_PATTERN = Regex(""""presence"\s*:\s*"(\w+)"""", RegexOption.IGNORE_CASE)
    private val CONFIDENCE_PATTERN = Regex(""""confidence"\s*:\s*([\d.]+)""")

    fun parse(raw: String): PresenceDetectionResult? {
        val jsonText = extractJsonObject(raw) ?: return null
        val presenceMatch = PRESENCE_PATTERN.find(jsonText) ?: return null
        val status = when (presenceMatch.groupValues[1].lowercase()) {
            "present" -> PresenceStatus.PRESENT
            "absent" -> PresenceStatus.ABSENT
            "uncertain" -> PresenceStatus.UNCERTAIN
            else -> return null
        }
        val confidence = CONFIDENCE_PATTERN.find(jsonText)
            ?.groupValues
            ?.get(1)
            ?.toFloatOrNull()
            ?.coerceIn(0f, 1f)
            ?: 0.5f
        return PresenceDetectionResult(status = status, confidence = confidence)
    }

    private fun extractJsonObject(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) {
            val end = trimmed.lastIndexOf('}')
            if (end > 0) return trimmed.substring(0, end + 1)
        }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return null
    }
}
