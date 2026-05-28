package com.example.mydeskrobot.domain.llm

import com.example.mydeskrobot.presentation.settings.toDomain
import com.example.mydeskrobot.presentation.settings.toFormState
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmSettingsFormTest {

    @Test
    fun formRoundTripPreservesProvider() {
        val original = LlmSettings(
            provider = LlmProvider.GEMINI,
            baseUrl = "http://localhost/",
            textModel = "gemini-2.0-flash",
            visionModel = "gemini-2.0-flash",
            apiKey = "secret",
        )
        val restored = original.toFormState().toDomain()
        assertEquals(original.provider, restored.provider)
        assertEquals(original.textModel, restored.textModel)
        assertEquals(original.apiKey, restored.apiKey)
    }

    @Test
    fun resolvedVisionModelFallsBackToTextModel() {
        val settings = LlmSettings(textModel = "model-a", visionModel = "")
        assertEquals("model-a", settings.resolvedVisionModel())
    }
}
