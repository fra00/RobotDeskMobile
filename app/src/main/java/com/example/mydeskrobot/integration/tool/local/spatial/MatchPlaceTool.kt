package com.example.mydeskrobot.integration.tool.local.spatial

import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.domain.spatial.MatchConfidenceBand
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class MatchPlaceTool(
    private val placeRepository: SpatialPlaceRepository,
) : Tool {

    override val name: String = "match_place"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "Compare observed landmarks against memorized places. Returns best match and confidence band.",
        parameters = listOf(
            ToolParameter(
                name = "landmarks",
                type = "array",
                description = "Landmark strings from analyze_room_scene (Italian nouns)",
                required = true,
            ),
        ),
        returns = "best_match, confidence, band, candidates, inferred_room_type",
        example = """{"name": "match_place", "params": {"landmarks": ["scrivania", "monitor"]}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val landmarks = SpatialToolSupport.parseLandmarks(invocation.params)
        if (landmarks.isEmpty()) {
            return ToolResult.Error(message = "Parametro landmarks mancante", code = "MISSING_PARAM")
        }

        val result = placeRepository.matchLandmarks(landmarks)
        val candidates = result.candidates.map { candidate ->
            mapOf(
                "place_id" to candidate.placeId,
                "label" to candidate.label,
                "score" to candidate.score,
                "room_type" to candidate.roomType.name.lowercase(),
            )
        }

        return ToolResult.Success(
            data = buildMap {
                result.bestMatch?.let { best ->
                    put("best_match", mapOf(
                        "place_id" to best.placeId,
                        "label" to best.label,
                        "score" to best.score,
                        "room_type" to best.roomType.name.lowercase(),
                    ))
                }
                put("confidence", result.confidence)
                put("band", result.band.name.lowercase())
                put("candidates", candidates)
                put("inferred_room_type", result.inferredRoomType.name.lowercase())
                put("should_ask_user", result.band == MatchConfidenceBand.MEDIUM || result.band == MatchConfidenceBand.LOW)
            },
        )
    }
}
