package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Kotlin-derived mood adjustments for one user or LLM turn.
 */
data class TurnMoodSignals(
    val triggers: List<MoodTrigger> = emptyList(),
    val promptHints: List<String> = emptyList(),
    val llmEmotionValenceTier: LlmEmotionValenceTier = LlmEmotionValenceTier.FULL,
    val ephemeralIntensityScale: Float? = null,
)

enum class LlmEmotionValenceTier {
    /** Full valence delta from [LlmEmotionValenceMapper]. */
    FULL,
    /** Routine ack — no positive valence from happy/loving. */
    ROUTINE,
    /** Ephemeral only — no valence shift. */
    NONE,
}
