package com.example.mydeskrobot.data.llm

import android.util.Log
import com.example.mydeskrobot.domain.model.LlmAssistantReply
import com.example.mydeskrobot.domain.repository.LlmRepository
import com.example.mydeskrobot.domain.vision.CapturedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlmRepositoryImpl(
    private val api: OpenAiChatApi,
    private val textModel: String,
    private val visionModel: String,
    private val systemPrompt: String,
    private val responseParser: LlmResponseParser = LlmResponseParser(),
) : LlmRepository {

    override fun isConfigured(): Boolean =
        textModel.isNotBlank() && systemPrompt.isNotBlank()

    override suspend fun ask(prompt: String): Result<LlmAssistantReply> =
        withContext(Dispatchers.IO) {
            val trimmed = prompt.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Empty prompt"))
            }

            runCatching {
                val response = api.createChatCompletion(
                    request = ChatCompletionRequest(
                        model = textModel,
                        messages = listOf(
                            ChatMessage(role = "system", content = systemPrompt),
                            ChatMessage(role = "user", content = trimmed),
                        ),
                    ),
                )
                parseResponse(response)
            }
        }

    override suspend fun askWithImage(
        userPrompt: String,
        image: CapturedImage,
    ): Result<LlmAssistantReply> =
        withContext(Dispatchers.IO) {
            val trimmed = userPrompt.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Empty prompt"))
            }
            val model = visionModel.ifBlank { textModel }
            if (model.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Vision model not configured"))
            }

            runCatching {
                val dataUrl = ImageDataUrlEncoder.toDataUrl(image)
                Log.i(TAG, "Vision request: model=$model imageBytes=${image.jpegBytes.size}")
                val response = api.createVisionChatCompletion(
                    request = VisionChatCompletionRequest(
                        model = model,
                        messages = listOf(
                            VisionChatMessage(
                                role = "system",
                                content = listOf(
                                    VisionContentPart(
                                        type = "text",
                                        text = systemPrompt,
                                    ),
                                ),
                            ),
                            VisionChatMessage(
                                role = "user",
                                content = listOf(
                                    VisionContentPart(
                                        type = "text",
                                        text = buildVisionUserText(trimmed),
                                    ),
                                    VisionContentPart(
                                        type = "image_url",
                                        imageUrl = VisionImageUrl(url = dataUrl),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val parsed = parseResponse(response)
                parsed.copy(imageRequired = false)
            }
        }

    private fun parseResponse(response: ChatCompletionResponse): LlmAssistantReply {
        val raw = response.choices
            ?.firstOrNull()
            ?.message
            ?.content
            ?.trim()
            .orEmpty()
        return responseParser.parse(raw)
    }

    private fun buildVisionUserText(userPrompt: String): String =
        """
        User question: "$userPrompt"

        Analyze the attached photo and respond in Italian in the JSON format required by the system prompt.
        Always set "imageRequired": false in this turn.
        """.trimIndent()

    companion object {
        private const val TAG = "LlmRepository"
        /** Ack breve se il modello chiede immagine senza testo parlato. */
        const val DEFAULT_IMAGE_ACK = "Ok, do un'occhiata."
    }
}
