package com.example.mydeskrobot.domain.speech

/**
 * Detects yes/no answers for pending confirmation flows (e.g. phone call).
 */
object VoiceConfirmationMatcher {

    private val YES_PHRASES = listOf(
        "si", "sì", "ok", "okay", "procedi", "confermo", "vai", "certo", "certamente",
        "va bene", "d accordo", "d'accordo", "dacordo", "esatto", "yes", "yep", "procediamo",
        "fallo", "chiama", "telefona",
    )

    private val NO_PHRASES = listOf(
        "no", "annulla", "annullare", "stop", "lascia perdere", "lascia stare",
        "non procedere", "non chiamare", "cancel", "fermati", "ferma",
    )

    fun parse(transcript: String): VoiceConfirmationDecision {
        val normalized = normalize(transcript)
        if (normalized.isBlank()) return VoiceConfirmationDecision.UNCLEAR

        if (matchesAny(normalized, YES_PHRASES)) return VoiceConfirmationDecision.YES
        if (matchesAny(normalized, NO_PHRASES)) return VoiceConfirmationDecision.NO

        if (normalized.length > 40) return VoiceConfirmationDecision.NOT_CONFIRMATION
        return VoiceConfirmationDecision.UNCLEAR
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

    private fun matchesAny(normalized: String, phrases: List<String>): Boolean {
        if (normalized in phrases) return true
        return phrases.any { phrase ->
            normalized == phrase ||
                normalized.startsWith("$phrase ") ||
                normalized.endsWith(" $phrase") ||
                normalized.contains(" $phrase ")
        }
    }
}

enum class VoiceConfirmationDecision {
    YES,
    NO,
    UNCLEAR,
    NOT_CONFIRMATION,
}
