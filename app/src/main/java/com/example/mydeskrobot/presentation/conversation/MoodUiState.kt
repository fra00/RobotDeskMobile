package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.domain.mood.MoodValenceConfig

/**
 * Mood debug snapshot for the wellbeing dialog (persistent valence + ephemeral expression).
 */
data class MoodUiState(
    val valence: Float = MoodValenceConfig.DEFAULT_BASELINE,
    val baseline: Float = MoodValenceConfig.DEFAULT_BASELINE,
    val valenceMin: Float = MoodValenceConfig.VALENCE_MIN,
    val valenceMax: Float = MoodValenceConfig.VALENCE_MAX,
    val baseEmotion: RobotEmotion = RobotEmotion.NEUTRAL,
    val baseIntensity: Float = 0.5f,
    val displayEmotion: RobotEmotion = RobotEmotion.NEUTRAL,
    val displayIntensity: Float = 0.5f,
    val reason: MoodReason? = null,
    val durationMinutes: Long = 0L,
    val idleMinutes: Long = 0L,
    val recentDeltas: List<MoodDeltaUi> = emptyList(),
    val ephemeralEmotion: RobotEmotion? = null,
    val ephemeralIntensity: Float? = null,
    val ephemeralRemainingSeconds: Long? = null,
    val promptSnapshot: String = "",
)

data class MoodDeltaUi(
    val event: String,
    val deltaFormatted: String,
)
