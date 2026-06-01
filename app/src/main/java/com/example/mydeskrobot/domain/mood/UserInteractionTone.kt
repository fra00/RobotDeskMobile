package com.example.mydeskrobot.domain.mood

enum class UserInteractionTone {
    APOLOGY,
    POSITIVE,
    NEUTRAL,
}

object UserInteractionToneDetector {

    private val APOLOGY_KEYWORDS = setOf(
        "scusa", "scusami", "mi dispiace", "perdonami", "perdono",
        "sorry", "my bad", "chiedo scusa",
    )

    private val POSITIVE_KEYWORDS = setOf(
        "grazie", "fantastico", "bravo", "utile", "perfetto",
        "ottimo", "grande", "wow", "bello", "interessante",
        "ben fatto", "complimenti",
    )

    fun detect(text: String): UserInteractionTone {
        val lower = text.lowercase()
        if (containsAny(lower, APOLOGY_KEYWORDS)) return UserInteractionTone.APOLOGY
        if (containsAny(lower, POSITIVE_KEYWORDS)) return UserInteractionTone.POSITIVE
        return UserInteractionTone.NEUTRAL
    }

    private fun containsAny(text: String, keywords: Set<String>): Boolean =
        keywords.any { text.contains(it) }
}
