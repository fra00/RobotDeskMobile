package com.example.mydeskrobot.integration.tool.local.spatial

import com.example.mydeskrobot.domain.spatial.RoomLandmarks
import com.example.mydeskrobot.domain.spatial.RoomType

internal object SpatialToolSupport {

    fun parseLandmarks(params: Map<String, Any?>): List<String> {
        val raw = params["landmarks"]
        return when (raw) {
            is List<*> -> RoomLandmarks.normalizeAll(raw.filterIsInstance<String>())
            is String -> RoomLandmarks.normalizeAll(raw.split(',', ';').map { it.trim() })
            else -> emptyList()
        }
    }

    fun parseRoomType(params: Map<String, Any?>): RoomType =
        RoomType.fromRaw(params["room_type"] as? String ?: params["roomType"] as? String)

    fun parseOptionalLong(params: Map<String, Any?>, key: String): Long? =
        when (val raw = params[key]) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }

    fun parseConfidence(params: Map<String, Any?>): Float =
        when (val raw = params["confidence"]) {
            is Number -> raw.toFloat().coerceIn(0f, 1f)
            is String -> raw.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f
            else -> 0.5f
        }
}
