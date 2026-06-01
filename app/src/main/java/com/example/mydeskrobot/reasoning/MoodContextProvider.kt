package com.example.mydeskrobot.reasoning

/**
 * Supplies autonomous robot mood for the LLM system prompt.
 */
fun interface MoodContextProvider {
    suspend fun buildContextSection(): String
}
