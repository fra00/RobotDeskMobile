package com.example.mydeskrobot.integration.tool.local.spatial

import android.content.Context
import com.example.mydeskrobot.domain.spatial.SpatialScanSession
import com.example.mydeskrobot.domain.vision.VisionImageCapture
import com.example.mydeskrobot.integration.spatial.RoomSceneAnalyzer
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition

class AnalyzeRoomSceneTool(
    private val analyzer: RoomSceneAnalyzer,
    private val onAnalyzed: (landmarks: List<String>) -> Unit = {},
) : Tool {

    constructor(
        visionCapture: VisionImageCapture,
        llmClient: LlmClient,
        context: Context,
        onAnalyzed: (landmarks: List<String>) -> Unit = {},
    ) : this(
        analyzer = RoomSceneAnalyzer(visionCapture, llmClient, context),
        onAnalyzed = onAnalyzed,
    )

    override val name: String = "analyze_room_scene"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "Capture one photo and extract room landmarks for spatial memory. " +
            "Use before match_place/save_place. Combine with move_body_joint for multi-angle scan.",
        parameters = emptyList(),
        returns = "landmarks (array), room_type_hint, description, confidence",
        example = """{"name": "analyze_room_scene", "params": {}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        return analyzer.analyze().fold(
            onSuccess = { analysis ->
                onAnalyzed(analysis.landmarks)
                ToolResult.Success(
                    data = buildMap {
                        put("landmarks", analysis.landmarks)
                        put("room_type_hint", analysis.roomTypeHint.name.lowercase())
                        put("description", analysis.description)
                        put("confidence", analysis.confidence)
                        put("scan_progress", SpatialScanSession.progressLabel())
                        put("scan_ready_for_save", SpatialScanSession.isReadyForNewPlaceSave())
                    },
                )
            },
            onFailure = { error ->
                ToolResult.Error(
                    message = "Analisi stanza non riuscita: ${error.message}",
                    code = "ROOM_SCENE_ERROR",
                    recoverable = true,
                )
            },
        )
    }
}
