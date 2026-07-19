package com.example.mydeskrobot.domain.mood

/**
 * Tone of the user's last utterance toward the robot.
 * Judged by the LLM (JSON `user_tone` field) — no Kotlin keyword heuristics.
 */
enum class UserInteractionTone {
    APOLOGY,
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
    ;

    companion object {
        fun fromLlmValue(value: String?): UserInteractionTone? = when (value?.trim()?.lowercase()) {
            "apology" -> APOLOGY
            "positive" -> POSITIVE
            "negative" -> NEGATIVE
            "neutral" -> NEUTRAL
            else -> null
        }
    }
}
