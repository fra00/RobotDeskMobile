package com.example.mydeskrobot.integration.vision

import com.example.mydeskrobot.domain.spatial.RoomLandmarks
import com.example.mydeskrobot.domain.spatial.RoomSceneAnalysis
import com.example.mydeskrobot.domain.spatial.RoomType

object RoomSceneResponseParser {

    private val LANDMARKS_ARRAY_PATTERN = Regex(""""landmarks"\s*:\s*\[([^\]]*)]""", RegexOption.DOT_MATCHES_ALL)
    private val ROOM_TYPE_PATTERN = Regex(""""room_type_hint"\s*:\s*"(\w+)"""", RegexOption.IGNORE_CASE)
    private val DESCRIPTION_PATTERN = Regex(""""description"\s*:\s*"((?:\\.|[^"\\])*)"""")
    private val CONFIDENCE_PATTERN = Regex(""""confidence"\s*:\s*([\d.]+)""")
    private val STRING_ITEM_PATTERN = Regex(""""([^"\\]*)"""")

    fun parse(raw: String): RoomSceneAnalysis? {
        val jsonText = extractJsonObject(raw) ?: return null
        val landmarksBlock = LANDMARKS_ARRAY_PATTERN.find(jsonText)?.groupValues?.get(1).orEmpty()
        val landmarks = STRING_ITEM_PATTERN.findAll(landmarksBlock)
            .map { it.groupValues[1] }
            .map { RoomLandmarks.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (landmarks.isEmpty()) return null

        val roomType = ROOM_TYPE_PATTERN.find(jsonText)
            ?.groupValues?.get(1)
            ?.let { RoomType.fromRaw(it) }
            ?: RoomType.UNKNOWN
        val description = DESCRIPTION_PATTERN.find(jsonText)?.groupValues?.get(1)?.trim().orEmpty()
        val confidence = CONFIDENCE_PATTERN.find(jsonText)
            ?.groupValues?.get(1)
            ?.toFloatOrNull()
            ?.coerceIn(0f, 1f)
            ?: 0.5f

        return RoomSceneAnalysis(
            landmarks = landmarks,
            roomTypeHint = roomType,
            description = description,
            confidence = confidence,
        )
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
