package com.example.mydeskrobot.reasoning

/**
 * Supplies spatial context (current room) for the LLM system prompt.
 */
fun interface SpatialContextProvider {
    suspend fun buildContextSection(): String
}
