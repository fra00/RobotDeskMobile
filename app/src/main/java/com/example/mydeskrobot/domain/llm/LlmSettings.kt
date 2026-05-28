package com.example.mydeskrobot.domain.llm

data class LlmSettings(
    val provider: LlmProvider = LlmProvider.LM_STUDIO,
    val baseUrl: String = "",
    val textModel: String = "",
    val visionModel: String = "",
    val apiKey: String = "",
) {
    fun resolvedVisionModel(): String = visionModel.trim().ifBlank { textModel.trim() }
}
