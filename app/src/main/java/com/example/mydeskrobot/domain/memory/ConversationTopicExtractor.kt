package com.example.mydeskrobot.domain.memory

import com.example.mydeskrobot.memory.MemoryTopicMatcher

/**
 * Lightweight topic extraction for daily working memory (no LLM).
 */
object ConversationTopicExtractor {

    private val WEATHER_HINTS = setOf("meteo", "tempo", "pioggia", "sole", "neve", "temperature")
    private val MUSIC_HINTS = setOf("musica", "spotify", "ascoltare", "canzone", "canzoni")

    fun extract(userPhrase: String): String? {
        val lower = userPhrase.trim().lowercase()
        if (lower.isBlank()) return null

        if (WEATHER_HINTS.any { lower.contains(it) }) return "meteo"
        if (MUSIC_HINTS.any { lower.contains(it) }) return "musica"

        return MemoryTopicMatcher.tokenize(lower)
            .firstOrNull { it.length >= 4 }
    }
}
