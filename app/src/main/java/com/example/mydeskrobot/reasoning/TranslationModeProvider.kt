package com.example.mydeskrobot.reasoning

/**
 * Supplies active continuous translation mode for the LLM system prompt.
 */
fun interface TranslationModeProvider {
    suspend fun buildContextSection(): String
}
