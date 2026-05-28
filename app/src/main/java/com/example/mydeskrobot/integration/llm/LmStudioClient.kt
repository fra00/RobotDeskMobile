package com.example.mydeskrobot.integration.llm

import com.example.mydeskrobot.data.llm.ChatCompletionRequest
import com.example.mydeskrobot.data.llm.ChatMessage
import com.example.mydeskrobot.data.llm.OpenAiChatApi
import com.example.mydeskrobot.data.llm.VisionChatCompletionRequest
import com.example.mydeskrobot.data.llm.VisionChatMessage
import com.example.mydeskrobot.data.llm.VisionContentPart
import com.example.mydeskrobot.data.llm.VisionImageUrl
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.llm.LlmResponse
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64

/**
 * LLM Client implementation for LM Studio (OpenAI-compatible API).
 * Also works with OpenAI, Groq, Together, and other OpenAI-compatible providers.
 */
class LmStudioClient(
    private val api: OpenAiChatApi,
    private val textModel: String,
    private val visionModel: String,
) : LlmClient {
    
    override suspend fun chat(
        messages: List<ConversationMessage>,
        systemPrompt: String,
    ): Result<LlmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val chatMessages = buildChatMessages(messages, systemPrompt)
            
            val response = api.createChatCompletion(
                request = ChatCompletionRequest(
                    model = textModel,
                    messages = chatMessages,
                )
            )
            
            val content = response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()
                .orEmpty()
            
            val finishReason = response.choices
                ?.firstOrNull()
                ?.finishReason
            
            LlmResponse(
                content = content,
                model = textModel,
                finishReason = finishReason,
            )
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
            val visionMessages = buildVisionMessages(messages, systemPrompt, imageBytes)
            
            val response = api.createVisionChatCompletion(
                request = VisionChatCompletionRequest(
                    model = model,
                    messages = visionMessages,
                )
            )
            
            val content = response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()
                .orEmpty()
            
            val finishReason = response.choices
                ?.firstOrNull()
                ?.finishReason
            
            LlmResponse(
                content = content,
                model = model,
                finishReason = finishReason,
            )
        }
    }
    
    override fun isConfigured(): Boolean {
        return textModel.isNotBlank()
    }
    
    private fun buildChatMessages(
        messages: List<ConversationMessage>,
        systemPrompt: String,
    ): List<ChatMessage> {
        return buildList {
            add(ChatMessage(role = "system", content = systemPrompt))
            messages.forEach { msg ->
                add(ChatMessage(role = msg.role, content = msg.content))
            }
        }
    }
    
    private fun buildVisionMessages(
        messages: List<ConversationMessage>,
        systemPrompt: String,
        imageBytes: ByteArray,
    ): List<VisionChatMessage> {
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val dataUrl = "data:image/jpeg;base64,$base64Image"
        
        return buildList {
            add(
                VisionChatMessage(
                    role = "system",
                    content = listOf(
                        VisionContentPart(type = "text", text = systemPrompt)
                    )
                )
            )
            
            messages.dropLast(1).forEach { msg ->
                add(
                    VisionChatMessage(
                        role = msg.role,
                        content = listOf(
                            VisionContentPart(type = "text", text = msg.content)
                        )
                    )
                )
            }
            
            val lastMessage = messages.lastOrNull()
            if (lastMessage != null) {
                add(
                    VisionChatMessage(
                        role = lastMessage.role,
                        content = listOf(
                            VisionContentPart(
                                type = "text",
                                text = buildVisionUserText(lastMessage.content)
                            ),
                            VisionContentPart(
                                type = "image_url",
                                imageUrl = VisionImageUrl(url = dataUrl)
                            )
                        )
                    )
                )
            }
        }
    }
    
    private fun buildVisionUserText(userPrompt: String): String =
        """
        User question: "$userPrompt"

        Analyze the attached photo and respond in Italian in the JSON format required by the system prompt.
        """.trimIndent()
}
