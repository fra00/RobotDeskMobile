package com.example.mydeskrobot.data.predictivity

import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.proactive.ProactivityConstants

object HabitSlotCodec {
    fun encode(slot: HabitSlot): String {
        val rawLabelsJson = slot.rawLabels.sorted().joinToString(",") { "\"${escapeJson(it)}\"" }
        val json = buildString {
            append('{')
            append("\"slotKey\":\"").append(escapeJson(slot.slotKey)).append('"')
            append(",\"canonicalLabel\":\"").append(escapeJson(slot.canonicalLabel)).append('"')
            append(",\"displayLabel\":\"").append(escapeJson(slot.displayLabel)).append('"')
            append(",\"typicalTimeMinutes\":").append(slot.typicalTimeMinutes)
            append(",\"timeToleranceMinutes\":").append(slot.timeToleranceMinutes)
            append(",\"hitCount\":").append(slot.hitCount)
            slot.lastHitDayKey?.let { dayKey ->
                append(",\"lastHitDayKey\":\"").append(escapeJson(dayKey)).append('"')
            }
            append(",\"confidence\":").append(slot.confidence.toDouble())
            append(",\"source\":\"").append(escapeJson(slot.source)).append('"')
            append(",\"rawLabels\":[").append(rawLabelsJson).append(']')
            append('}')
        }
        return ProactivityConstants.HABIT_SLOT_VALUE_PREFIX + json
    }

    fun decode(value: String): HabitSlot? {
        val trimmed = value.trim()
        if (!trimmed.startsWith(ProactivityConstants.HABIT_SLOT_VALUE_PREFIX)) return null
        val jsonText = trimmed.removePrefix(ProactivityConstants.HABIT_SLOT_VALUE_PREFIX)
        if (jsonText.isBlank() || jsonText == "null") return null

        val slotKey = extractString(jsonText, "slotKey")?.trim().orEmpty()
        if (slotKey.isBlank()) return null

        val rawLabels = extractStringArray(jsonText, "rawLabels")
        return HabitSlot(
            slotKey = slotKey,
            canonicalLabel = extractString(jsonText, "canonicalLabel").orEmpty().trim(),
            displayLabel = extractString(jsonText, "displayLabel").orEmpty().trim(),
            typicalTimeMinutes = extractInt(jsonText, "typicalTimeMinutes"),
            timeToleranceMinutes = extractInt(
                jsonText,
                "timeToleranceMinutes",
                ProactivityConstants.PREDICTIVITY_TIME_TOLERANCE_MINUTES,
            ),
            hitCount = extractInt(jsonText, "hitCount"),
            lastHitDayKey = extractString(jsonText, "lastHitDayKey")?.trim()?.takeIf { it.isNotEmpty() },
            confidence = extractDouble(jsonText, "confidence").toFloat(),
            rawLabels = rawLabels,
            source = extractString(jsonText, "source")?.trim().orEmpty().ifBlank { "activity_log_miner" },
        )
    }

    fun externalRef(slotKey: String): String =
        ProactivityConstants.HABIT_SLOT_EXTERNAL_REF_PREFIX + slotKey

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unescapeJson(value: String): String =
        value.replace("\\\"", "\"").replace("\\\\", "\\")

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return pattern.find(json)?.groupValues?.get(1)?.let(::unescapeJson)
    }

    private fun extractInt(json: String, key: String, default: Int = 0): Int =
        Regex(""""$key"\s*:\s*(-?\d+)""")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: default

    private fun extractDouble(json: String, key: String, default: Double = 0.0): Double =
        Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
            ?: default

    private fun extractStringArray(json: String, key: String): Set<String> {
        val pattern = Regex(""""$key"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val body = pattern.find(json)?.groupValues?.get(1).orEmpty()
        if (body.isBlank()) return emptySet()
        return STRING_LITERAL_REGEX.findAll(body)
            .map { unescapeJson(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private val STRING_LITERAL_REGEX = Regex(""""((?:\\.|[^"\\])*)"""")
}
