package com.example.mydeskrobot.domain.mood

/**
 * Valence deltas and bounds for persistent robot wellbeing (-1…+1).
 */
data class MoodValenceConfig(
    val defaultBaseline: Float = DEFAULT_BASELINE,
    val valenceMin: Float = VALENCE_MIN,
    val valenceMax: Float = VALENCE_MAX,
    val taskCompleted: Float = 0.08f,
    val positiveInteraction: Float = 0.20f,
    val negativeInteraction: Float = -0.18f,
    val userApology: Float = 0.12f,
    val eyePokeTier1: Float = -0.10f,
    val eyePokeTier2: Float = -0.20f,
    val eyePokeTier3: Float = -0.30f,
    val idleBored: Float = -0.05f,
    val idleDrowsy: Float = -0.10f,
    val decayTowardBaseline: Float = 0.08f,
    val maxRecentDeltas: Int = 5,
) {
    companion object {
        const val DEFAULT_BASELINE = 0.1f
        const val VALENCE_MIN = -0.4f
        const val VALENCE_MAX = 0.85f
    }
}

data class MoodDelta(
    val event: String,
    val delta: Float,
    val at: Long,
)
