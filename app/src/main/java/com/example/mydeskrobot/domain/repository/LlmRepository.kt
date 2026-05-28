package com.example.mydeskrobot.domain.repository

import com.example.mydeskrobot.domain.model.LlmAssistantReply
import com.example.mydeskrobot.domain.vision.CapturedImage

interface LlmRepository {
    suspend fun ask(prompt: String): Result<LlmAssistantReply>

    suspend fun askWithImage(
        userPrompt: String,
        image: CapturedImage,
    ): Result<LlmAssistantReply>

    fun isConfigured(): Boolean
}
