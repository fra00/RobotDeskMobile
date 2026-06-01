package com.example.mydeskrobot.integration.vision

import android.content.Context
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.domain.vision.PresenceDetectionResult
import com.example.mydeskrobot.domain.vision.VisionImageCapture
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage

/**
 * Captures a frame and runs a focused vision LLM call for desk presence only.
 */
class PresenceAnalyzer(
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
        systemPrompt = LlmPromptLoader.loadPresenceDetectionPrompt(context),
    )

    suspend fun detect(): Result<PresenceDetectionResult> {
        if (!llmClient.isConfigured()) {
            return Result.failure(IllegalStateException("LLM non configurato"))
        }

        val captureResult = visionCapture.captureJpeg()
        val image = captureResult.getOrElse { return Result.failure(it) }

        val llmResult = llmClient.chatWithImage(
            messages = listOf(ConversationMessage.User("Analyze presence in this frame.")),
            systemPrompt = systemPrompt,
            imageBytes = image.jpegBytes,
        )

        val content = llmResult.getOrElse { return Result.failure(it) }.content
        val parsed = PresenceResponseParser.parse(content)
            ?: return Result.failure(IllegalStateException("Risposta presenza non valida: $content"))

        return Result.success(parsed)
    }
}
