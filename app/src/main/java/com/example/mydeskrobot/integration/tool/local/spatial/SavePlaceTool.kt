package com.example.mydeskrobot.integration.tool.local.spatial

import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.memory.unified.UnifiedMemoryWriter
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class SavePlaceTool(
    private val placeRepository: SpatialPlaceRepository,
    private val memoryWriter: UnifiedMemoryWriter,
) : Tool {

    override val name: String = "save_place"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "Create or update a memorized room/place from landmarks and optional label.",
        parameters = listOf(
            ToolParameter("label", "string", "Room name in Italian (e.g. studio, camera)", required = true),
            ToolParameter("landmarks", "array", "Landmark list from vision", required = true),
            ToolParameter("description", "string", "Short Italian summary", required = false),
            ToolParameter("room_type", "string", "bedroom|study|kitchen|living_room|bathroom|hallway|unknown", required = false),
            ToolParameter("place_id", "integer", "Existing place id to update", required = false),
            ToolParameter("aliases", "array", "Alternative names", required = false),
        ),
        returns = "place_id, label, room_type, landmarks",
        example = """{"name": "save_place", "params": {"label": "studio", "landmarks": ["scrivania","computer"]}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val label = (invocation.params["label"] as? String)?.trim().orEmpty()
        if (label.isBlank()) {
            return ToolResult.Error(message = "Parametro label mancante", code = "MISSING_PARAM")
        }
        val landmarks = SpatialToolSupport.parseLandmarks(invocation.params)
        if (landmarks.isEmpty()) {
            return ToolResult.Error(message = "Parametro landmarks mancante", code = "MISSING_PARAM")
        }

        val description = (invocation.params["description"] as? String).orEmpty()
        val roomType = SpatialToolSupport.parseRoomType(invocation.params)
        val placeId = SpatialToolSupport.parseOptionalLong(invocation.params, "place_id")
        val aliases = when (val raw = invocation.params["aliases"]) {
            is List<*> -> raw.filterIsInstance<String>()
            else -> emptyList()
        }

        val id = placeRepository.savePlace(
            label = label,
            landmarks = landmarks,
            description = description,
            roomType = roomType,
            aliases = aliases,
            placeId = placeId,
        )

        val saved = placeRepository.getById(id)
        memoryWriter.onPlaceSaved(
            placeId = id,
            label = saved?.label ?: label,
            landmarks = saved?.landmarks ?: landmarks,
            roomType = (saved?.roomType ?: roomType).name.lowercase(),
            description = saved?.description ?: description,
        )
        return ToolResult.Success(
            data = mapOf(
                "place_id" to id,
                "label" to (saved?.label ?: label),
                "room_type" to (saved?.roomType?.name?.lowercase() ?: roomType.name.lowercase()),
                "landmarks" to (saved?.landmarks ?: landmarks),
            ),
        )
    }
}
