package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import kotlin.math.abs

/**
 * Short-lived expression from LLM JSON [emotion]; does not change persistent valence.
 */
data class EphemeralExpression(
    val emotion: RobotEmotion,
    val intensity: Float,
    val expiresAt: Long,
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean = now < expiresAt
}

object EphemeralExpressionPolicy {

    private val NON_EPHEMERAL = setOf(
        RobotEmotion.LISTENING,
        RobotEmotion.SPEAKING,
        RobotEmotion.THINKING,
        RobotEmotion.SLEEPING,
    )

    fun create(
        emotion: RobotEmotion?,
        valence: Float = MoodValenceConfig.DEFAULT_BASELINE,
        baseline: Float = MoodValenceConfig.DEFAULT_BASELINE,
        intensityScale: Float? = null,
        now: Long = System.currentTimeMillis(),
    ): EphemeralExpression? {
        if (emotion == null || emotion in NON_EPHEMERAL) return null
        val ttlMs = ttlMsFor(emotion) ?: return null
        val baseIntensity = when (emotion) {
            RobotEmotion.HAPPY, RobotEmotion.LOVING ->
                eventIntensity(valence, baseline, defaultIntensity(emotion))
            else -> defaultIntensity(emotion)
        }
        val intensity = (baseIntensity * (intensityScale ?: 1f)).coerceIn(0.25f, 0.85f)
        return EphemeralExpression(
            emotion = emotion,
            intensity = intensity,
            expiresAt = now + ttlMs,
        )
    }

    fun ttlMsFor(emotion: RobotEmotion): Long? = when (emotion) {
        RobotEmotion.NEUTRAL -> 14_000L
        RobotEmotion.ANGRY, RobotEmotion.SURPRISED -> 30_000L
        RobotEmotion.HAPPY, RobotEmotion.LOVING -> 25_000L
        RobotEmotion.CONFUSED, RobotEmotion.SAD, RobotEmotion.BORED, RobotEmotion.DROWSY -> 40_000L
        else -> null
    }

    private fun defaultIntensity(emotion: RobotEmotion): Float = when (emotion) {
        RobotEmotion.NEUTRAL -> 0.45f
        RobotEmotion.ANGRY -> 0.75f
        RobotEmotion.HAPPY, RobotEmotion.LOVING -> 0.7f
        RobotEmotion.SURPRISED -> 0.65f
        else -> 0.55f
    }

    private fun eventIntensity(valence: Float, baseline: Float, fallback: Float): Float {
        val span = abs(valence - baseline)
        return (0.35f + span * 1.2f).coerceIn(0.35f, 0.75f).let { derived ->
            if (derived < fallback * 0.6f) fallback * 0.6f else derived
        }
    }
}
