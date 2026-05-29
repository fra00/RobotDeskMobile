package com.example.mydeskrobot.reasoning

/**
 * Supplies active robot context for the LLM system prompt.
 */
fun interface RobotContextProvider {
    suspend fun buildContextSection(): String
}
