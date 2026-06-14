package com.example.mydeskrobot.integration.tool.local.spatial

import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition

class ListPlacesTool(
    private val placeRepository: SpatialPlaceRepository,
) : Tool {

    override val name: String = "list_places"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "List memorized rooms/places with landmarks for disambiguation.",
        parameters = emptyList(),
        returns = "places array with id, label, room_type, landmarks, description",
        example = """{"name": "list_places", "params": {}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val places = placeRepository.listActive().map { place ->
            mapOf(
                "place_id" to place.id,
                "label" to place.label,
                "room_type" to place.roomType.name.lowercase(),
                "landmarks" to place.landmarks,
                "description" to place.description,
                "aliases" to place.aliases,
                "last_seen_at" to place.lastSeenAt,
            )
        }
        return ToolResult.Success(
            data = mapOf(
                "count" to places.size,
                "places" to places,
            ),
        )
    }
}
