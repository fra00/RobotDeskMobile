package com.example.mydeskrobot.domain.mood

/**
 * Valence deltas and bounds for persistent robot wellbeing (-1…+1).
 */
data class MoodValenceConfig(
    val defaultBaseline: Float = DEFAULT_BASELINE,
    val valenceMin: Float = VALENCE_MIN,
    val valenceMax: Float = VALENCE_MAX,
    val taskCompleted: Float = 0.08f,
    val voiceTurnPresence: Float = 0.04f,
    val shortPhrasePresence: Float = 0.01f,
    val burstFatigue: Float = -0.06f,
    val repeatedPhraseFatigue: Float = -0.03f,
    val hotwordIdleBored: Float = -0.05f,
    val userApology: Float = 0.12f,
    val eyePokeTier1: Float = -0.10f,
    val eyePokeTier2: Float = -0.20f,
    val eyePokeTier3: Float = -0.30f,
    val idleBored: Float = -0.05f,
    val idleDrowsy: Float = -0.10f,
    val decayTowardBaseline: Float = 0.08f,
    val maxRecentDeltas: Int = 5,
    val llmSad: Float = LLM_SAD_DELTA,
    val llmAngry: Float = LLM_ANGRY_DELTA,
    val llmConfused: Float = LLM_CONFUSED_DELTA,
    val llmHappy: Float = LLM_HAPPY_DELTA,
    val llmLoving: Float = LLM_LOVING_DELTA,
    val llmBored: Float = LLM_BORED_DELTA,
) {
    companion object {
        const val DEFAULT_BASELINE = 0.1f
        const val VALENCE_MIN = -0.4f
        const val VALENCE_MAX = 0.85f
        const val LLM_SAD_DELTA = -0.15f
        const val LLM_ANGRY_DELTA = -0.18f
        const val LLM_CONFUSED_DELTA = -0.08f
        const val LLM_HAPPY_DELTA = 0.12f
        const val LLM_LOVING_DELTA = 0.10f
        const val LLM_BORED_DELTA = -0.05f
    }
}

data class MoodDelta(
    val event: String,
    val delta: Float,
    val at: Long,
)
