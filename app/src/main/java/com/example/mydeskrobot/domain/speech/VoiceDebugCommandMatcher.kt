package com.example.mydeskrobot.domain.speech

/**
 * Detects debug voice commands (e.g. force heartbeat tick).
 */
object VoiceDebugCommandMatcher {

    private val FORCE_HEARTBEAT_PHRASES = listOf(
        "forza heartbeat",
        "forza heart beat",
        "debug heartbeat",
        "test heartbeat",
        "forza proattivita",
        "forza proattività",
        "simula heartbeat",
        "simula proattivita",
        "simula proattività",
    )

    fun matchesForceHeartbeat(transcript: String): Boolean {
        val normalized = normalize(transcript)
        if (normalized.isBlank()) return false
        return FORCE_HEARTBEAT_PHRASES.any { phrase ->
            normalized == phrase ||
                normalized.startsWith("$phrase ") ||
                normalized.endsWith(" $phrase") ||
                normalized.contains(" $phrase ")
        }
    }

    private fun normalize(text: String): String =
        text.trim().lowercase()
            .replace("à", "a")
            .replace("è", "e")
            .replace("é", "e")
            .replace("ì", "i")
            .replace("ò", "o")
            .replace("ù", "u")
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
