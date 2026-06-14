package com.example.mydeskrobot.domain.mood

enum class UserInteractionTone {
    APOLOGY,
    POSITIVE,
    NEGATIVE,
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

    private val NEGATIVE_KEYWORDS = setOf(
        "idiota", "stupido", "stupida", "inutile", "zitto", "zitta",
        "fastidio", "lasciami", "vattene", "schiocco", "schifo",
        "odio", "pessimo", "orribile", "cazzo", "merda",
    )

    fun detect(text: String): UserInteractionTone {
        val lower = text.lowercase()
        if (containsAny(lower, APOLOGY_KEYWORDS)) return UserInteractionTone.APOLOGY
        if (containsAny(lower, NEGATIVE_KEYWORDS)) return UserInteractionTone.NEGATIVE
        if (containsAny(lower, POSITIVE_KEYWORDS)) return UserInteractionTone.POSITIVE
        return UserInteractionTone.NEUTRAL
    }

    private fun containsAny(text: String, keywords: Set<String>): Boolean =
        keywords.any { text.contains(it) }
}
