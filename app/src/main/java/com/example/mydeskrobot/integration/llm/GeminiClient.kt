package com.example.mydeskrobot.integration.llm

import android.util.Base64
import com.example.mydeskrobot.integration.llm.gemini.GeminiApi
import com.example.mydeskrobot.integration.llm.gemini.GeminiContent
import com.example.mydeskrobot.integration.llm.gemini.GeminiGenerateContentRequest
import com.example.mydeskrobot.integration.llm.gemini.GeminiGenerationConfig
import com.example.mydeskrobot.integration.llm.gemini.GeminiInlineData
import com.example.mydeskrobot.integration.llm.gemini.GeminiPart
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.llm.LlmResponse
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiClient(
    private val api: GeminiApi,
    private val apiKey: String,
    private val textModel: String,
    private val visionModel: String,
) : LlmClient {

    override suspend fun chat(
        messages: List<ConversationMessage>,
        systemPrompt: String,
    ): Result<LlmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildRequest(
                messages = messages,
                systemPrompt = systemPrompt,
                imageBytes = null,
            )
            val response = api.generateContent(
                model = textModel,
                apiKey = apiKey,
                request = request,
            )
            parseResponse(response, textModel)
        }
    }

    override suspend fun chatWithImage(
        messages: List<ConversationMessage>,
        systemPrompt: String,
        imageBytes: ByteArray,
    ): Result<LlmResponse> = withContext(Dispatchers.IO) {
        val model = visionModel.ifBlank { textModel }
        if (model.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Vision model not configured"))
        }
        runCatching {
            val request = buildRequest(
                messages = messages,
                systemPrompt = systemPrompt,
                imageBytes = imageBytes,
            )
            val response = api.generateContent(
                model = model,
                apiKey = apiKey,
                request = request,
            )
            parseResponse(response, model)
        }
    }

    override fun isConfigured(): Boolean {
        return apiKey.isNotBlank() && textModel.isNotBlank()
    }

    private fun buildRequest(
        messages: List<ConversationMessage>,
        systemPrompt: String,
        imageBytes: ByteArray?,
    ): GeminiGenerateContentRequest {
        val contents = messages.map { msg ->
            GeminiContent(
                role = mapRole(msg.role),
                parts = listOf(GeminiPart(text = msg.content)),
            )
        }.toMutableList()

        if (imageBytes != null && contents.isNotEmpty()) {
            val last = contents.removeAt(contents.lastIndex)
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val parts = buildList {
                addAll(last.parts)
                add(
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = "image/jpeg",
                            data = base64,
                        ),
                    ),
                )
            }
            contents.add(last.copy(parts = parts))
        }

        return GeminiGenerateContentRequest(
            contents = contents,
            systemInstruction = GeminiContent(
                role = "user",
                parts = listOf(GeminiPart(text = systemPrompt)),
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
            ),
        )
    }

    private fun mapRole(role: String): String = when (role) {
        "assistant" -> "model"
        else -> "user"
    }

    private fun parseResponse(
        response: com.example.mydeskrobot.integration.llm.gemini.GeminiGenerateContentResponse,
        model: String,
    ): LlmResponse {
        val candidate = response.candidates?.firstOrNull()
        val content = candidate?.content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.trim()
            .orEmpty()
        return LlmResponse(
            content = content,
            model = model,
            finishReason = candidate?.finishReason,
        )
    }
}
