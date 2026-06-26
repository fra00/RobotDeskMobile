package com.example.mydeskrobot.memory.unified

/**
 * Builds the cognitive index value for Log Day episode projections.
 * Includes optional source snippet so unified recall can answer social/content questions.
 */
object EpisodeProjectionValue {

    fun format(label: String, rawPhrase: String?): String {
        val normalizedLabel = label.trim()
        if (normalizedLabel.isBlank()) return ""
        val phrase = rawPhrase?.trim()?.takeIf { it.isNotBlank() } ?: return normalizedLabel
        if (phrase.equals(normalizedLabel, ignoreCase = true)) return normalizedLabel
        if (normalizedLabel.contains(phrase, ignoreCase = true)) return normalizedLabel
        return "$normalizedLabel — \"$phrase\""
    }
}
