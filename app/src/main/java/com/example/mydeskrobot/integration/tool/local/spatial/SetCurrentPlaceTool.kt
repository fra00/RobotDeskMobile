package com.example.mydeskrobot.integration.tool.local.spatial

import com.example.mydeskrobot.data.spatial.SpatialContextManager
import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.domain.spatial.SpatialResolution
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class SetCurrentPlaceTool(
    private val placeRepository: SpatialPlaceRepository,
    private val spatialContextManager: () -> SpatialContextManager,
) : Tool {

    override val name: String = "set_current_place"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "Set authoritative current room after match_place or user confirmation. Use place_id from match/save, or unknown to clear.",
        parameters = listOf(
            ToolParameter("place_id", "integer", "Memorized place id; omit or null for unknown", required = false),
            ToolParameter("confidence", "number", "0.0-1.0 match confidence", required = false),
            ToolParameter("resolution", "string", "autonomous|user_confirmed|user_named|unknown", required = false),
            ToolParameter("landmarks", "array", "Last scan landmarks snapshot", required = false),
        ),
        returns = "current_place label and confidence",
        example = """{"name": "set_current_place", "params": {"place_id": 2, "confidence": 0.82, "resolution": "autonomous"}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val placeId = SpatialToolSupport.parseOptionalLong(invocation.params, "place_id")
        val confidence = SpatialToolSupport.parseConfidence(invocation.params)
        val resolution = parseResolution(invocation.params["resolution"] as? String)
        val landmarks = SpatialToolSupport.parseLandmarks(invocation.params)

        if (placeId == null) {
            spatialContextManager().setCurrentPlace(
                placeId = null,
                label = null,
                confidence = 0f,
                resolution = SpatialResolution.UNKNOWN,
                landmarks = landmarks,
            )
            return ToolResult.Success(
                data = mapOf(
                    "current_place" to null,
                    "confidence" to 0f,
                    "resolution" to SpatialResolution.UNKNOWN.name.lowercase(),
                ),
            )
        }

        val place = placeRepository.getById(placeId)
            ?: return ToolResult.Error(message = "place_id $placeId non trovato", code = "NOT_FOUND")

        spatialContextManager().setCurrentPlace(
            placeId = place.id,
            label = place.label,
            confidence = confidence,
            resolution = resolution,
            landmarks = landmarks.ifEmpty { place.landmarks },
            roomType = place.roomType,
        )

        return ToolResult.Success(
            data = mapOf(
                "current_place" to place.label,
                "place_id" to place.id,
                "room_type" to place.roomType.name.lowercase(),
                "confidence" to confidence,
                "resolution" to resolution.name.lowercase(),
            ),
        )
    }

    private fun parseResolution(raw: String?): SpatialResolution {
        if (raw.isNullOrBlank()) return SpatialResolution.AUTONOMOUS
        return SpatialResolution.entries.find { it.name.equals(raw, ignoreCase = true) }
            ?: SpatialResolution.AUTONOMOUS
    }
}
