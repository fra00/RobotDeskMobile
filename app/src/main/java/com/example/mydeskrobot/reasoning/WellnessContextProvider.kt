package com.example.mydeskrobot.reasoning

/**
 * Supplies latest Wellness snapshot block for the LLM system prompt.
 */
fun interface WellnessContextProvider {
    suspend fun buildContextSection(): String
}
