package com.example.mydeskrobot.integration.llm.gemini

data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
)

data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null,
)

data class GeminiInlineData(
    val mimeType: String,
    val data: String,
)

data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null,
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)
