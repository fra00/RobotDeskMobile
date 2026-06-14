package com.example.mydeskrobot.integration.spatial

import android.content.Context
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.domain.spatial.RoomSceneAnalysis
import com.example.mydeskrobot.domain.vision.VisionImageCapture
import com.example.mydeskrobot.integration.vision.RoomSceneResponseParser
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage

class RoomSceneAnalyzer(
    private val visionCapture: VisionImageCapture,
    private val llmClient: LlmClient,
    private val systemPrompt: String,
) {
    constructor(
        visionCapture: VisionImageCapture,
        llmClient: LlmClient,
        context: Context,
    ) : this(
        visionCapture = visionCapture,
        llmClient = llmClient,
        systemPrompt = LlmPromptLoader.loadRoomLandmarksPrompt(context),
    )

    suspend fun analyze(): Result<RoomSceneAnalysis> {
        if (!llmClient.isConfigured()) {
            return Result.failure(IllegalStateException("LLM non configurato"))
        }

        val captureResult = visionCapture.captureJpeg()
        val image = captureResult.getOrElse { return Result.failure(it) }

        val llmResult = llmClient.chatWithImage(
            messages = listOf(ConversationMessage.User("Analyze room landmarks in this frame.")),
            systemPrompt = systemPrompt,
            imageBytes = image.jpegBytes,
        )

        val content = llmResult.getOrElse { return Result.failure(it) }.content
        val parsed = RoomSceneResponseParser.parse(content)
            ?: return Result.failure(IllegalStateException("Risposta landmark non valida: $content"))

        return Result.success(parsed)
    }
}
