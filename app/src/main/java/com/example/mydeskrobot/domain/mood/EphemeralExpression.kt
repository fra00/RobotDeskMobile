package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

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
        RobotEmotion.NEUTRAL,
        RobotEmotion.LISTENING,
        RobotEmotion.SPEAKING,
        RobotEmotion.THINKING,
        RobotEmotion.SLEEPING,
    )

    fun create(
        emotion: RobotEmotion?,
        now: Long = System.currentTimeMillis(),
    ): EphemeralExpression? {
        if (emotion == null || emotion in NON_EPHEMERAL) return null
        val ttlMs = ttlMsFor(emotion) ?: return null
        return EphemeralExpression(
            emotion = emotion,
            intensity = defaultIntensity(emotion),
            expiresAt = now + ttlMs,
        )
    }

    fun ttlMsFor(emotion: RobotEmotion): Long? = when (emotion) {
        RobotEmotion.ANGRY, RobotEmotion.SURPRISED -> 30_000L
        RobotEmotion.HAPPY, RobotEmotion.LOVING -> 25_000L
        RobotEmotion.CONFUSED, RobotEmotion.SAD, RobotEmotion.BORED, RobotEmotion.DROWSY -> 40_000L
        else -> null
    }

    private fun defaultIntensity(emotion: RobotEmotion): Float = when (emotion) {
        RobotEmotion.ANGRY -> 0.75f
        RobotEmotion.HAPPY, RobotEmotion.LOVING -> 0.7f
        RobotEmotion.SURPRISED -> 0.65f
        else -> 0.55f
    }
}
