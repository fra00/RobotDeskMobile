package com.example.mydeskrobot.data.llm

import com.squareup.moshi.Json

data class VisionChatCompletionRequest(
    val model: String,
    val messages: List<VisionChatMessage>,
)

data class VisionChatMessage(
    val role: String,
    val content: List<VisionContentPart>,
)

data class VisionContentPart(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url")
    val imageUrl: VisionImageUrl? = null,
)

data class VisionImageUrl(
    val url: String,
)
