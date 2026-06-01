package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.domain.vision.VisionImageCapture
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.integration.vision.PresenceAnalyzer
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition

/**
 * Silent presence check: captures camera frame and returns present/absent/uncertain.
 * Intended for optional heartbeat gating — does not infer work vs play.
 */
class DetectPresenceTool(
    private val presenceAnalyzer: PresenceAnalyzer,
) : Tool {

    constructor(
        visionCapture: VisionImageCapture,
        llmClient: LlmClient,
        context: Context,
    ) : this(PresenceAnalyzer(visionCapture, llmClient, context))

    override val name: String = "detect_presence"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Silently check if a person is at the desk (camera + vision). " +
                "Returns presence: present | absent | uncertain. " +
                "Use mainly during [SYSTEM_INPUT: heartbeat] when idle is long — " +
                "NOT for describing the scene. Does NOT detect work vs play.",
            parameters = emptyList(),
            returns = "presence (string), confidence (0.0-1.0)",
            example = """{"name": "detect_presence", "params": {}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val result = presenceAnalyzer.detect()
        return result.fold(
            onSuccess = { detection ->
                ToolResult.Success(
                    data = mapOf(
                        "presence" to detection.status.name.lowercase(),
                        "confidence" to detection.confidence,
                    ),
                )
            },
            onFailure = { error ->
                ToolResult.Error(
                    message = "Impossibile verificare la presenza: ${error.message}",
                    code = "PRESENCE_ERROR",
                    recoverable = true,
                )
            },
        )
    }
}
