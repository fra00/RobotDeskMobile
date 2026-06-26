package com.example.mydeskrobot.reasoning

/**
 * Supplies spatial context (current room) for the LLM system prompt.
 */
interface SpatialContextProvider {
    suspend fun buildContextSection(options: SpatialContextOptions = SpatialContextOptions()): String
}
