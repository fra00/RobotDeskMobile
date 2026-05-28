package com.example.mydeskrobot.data.llm

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.POST

interface OpenAiChatApi {

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse

    @POST("chat/completions")
    suspend fun createVisionChatCompletion(
        @Body request: VisionChatCompletionRequest,
    ): ChatCompletionResponse
}

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
)

data class ChatMessage(
    val role: String,
    val content: String,
)

data class ChatCompletionResponse(
    val choices: List<Choice>?,
)

data class Choice(
    val message: ChatMessage?,
    @Json(name = "finish_reason")
    val finishReason: String? = null,
)
