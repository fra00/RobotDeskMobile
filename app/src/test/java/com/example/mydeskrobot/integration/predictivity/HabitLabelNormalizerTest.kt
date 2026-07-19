package com.example.mydeskrobot.integration.predictivity

import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.llm.LlmResponse
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitLabelNormalizerTest {

    @Test
    fun `normalize uses fallback when llm not configured`() = runBlocking {
        val normalizer = HabitLabelNormalizer(
            llmClient = offlineClient(),
            normalizePrompt = "test",
        )
        val result = normalizer.normalize(listOf("Passeggiata Cane"))
        assertEquals("passeggiata_cane", result["Passeggiata Cane"])
    }

    @Test
    fun `normalize parses llm mappings json`() = runBlocking {
        val normalizer = HabitLabelNormalizer(
            llmClient = object : LlmClient {
                override suspend fun chat(
                    messages: List<ConversationMessage>,
                    systemPrompt: String,
                ): Result<LlmResponse> = Result.success(
                    LlmResponse(
                        content = """{"mappings":{"passeggiata cane":"passeggiata_cane","passeggiata Brina":"passeggiata_cane"}}""",
                    ),
                )

                override suspend fun chatWithImage(
                    messages: List<ConversationMessage>,
                    systemPrompt: String,
                    imageBytes: ByteArray,
                ): Result<LlmResponse> = Result.failure(IllegalStateException("unused"))

                override fun isConfigured() = true
            },
            normalizePrompt = "test",
        )

        val result = normalizer.normalize(listOf("passeggiata cane", "passeggiata Brina"))
        assertEquals("passeggiata_cane", result["passeggiata cane"])
        assertEquals("passeggiata_cane", result["passeggiata Brina"])
    }

    @Test
    fun `displayLabelFromCanonical humanizes underscore label`() {
        assertEquals("Passeggiata cane", HabitLabelNormalizer.displayLabelFromCanonical("passeggiata_cane"))
    }

    private fun offlineClient() = object : LlmClient {
        override suspend fun chat(
            messages: List<ConversationMessage>,
            systemPrompt: String,
        ): Result<LlmResponse> = Result.failure(IllegalStateException("offline"))

        override suspend fun chatWithImage(
            messages: List<ConversationMessage>,
            systemPrompt: String,
            imageBytes: ByteArray,
        ): Result<LlmResponse> = Result.failure(IllegalStateException("offline"))

        override fun isConfigured() = false
    }
}
