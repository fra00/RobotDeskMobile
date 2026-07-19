package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * TTS voice shaping (Android setPitch/setSpeechRate multipliers, 1.0 = engine default).
 */
data class TtsProsody(
    val pitch: Float,
    val rate: Float,
) {
    companion object {
        val NEUTRAL = TtsProsody(pitch = 1f, rate = 1f)
    }
}

/**
 * Maps the emotion the robot is expressing while speaking to a voice profile.
 *
 * Persistent wellbeing wins when the turn ephemeral is missing or only
 * neutral/thinking — otherwise a poke-driven angry mood would be masked by a
 * routine LLM "neutral" face and the voice would never change.
 */
object MoodProsodyMapper {

    private val STRONG_WELLBEING = setOf(
        RobotEmotion.ANGRY,
        RobotEmotion.SAD,
        RobotEmotion.BORED,
        RobotEmotion.DROWSY,
        RobotEmotion.SLEEPING,
        RobotEmotion.CONFUSED,
        RobotEmotion.HAPPY,
        RobotEmotion.LOVING,
    )

    fun forSpeech(
        mood: RobotMood,
        ephemeral: EphemeralExpression?,
        now: Long = System.currentTimeMillis(),
    ): TtsProsody {
        val active = ephemeral?.takeIf { it.isActive(now) }
        val emotion = resolveSpeechEmotion(mood.baseEmotion, active?.emotion)
        val intensity = when {
            active != null && emotion == active.emotion -> active.intensity
            else -> mood.intensity
        }
        return derive(emotion, intensity)
    }

    /**
     * Prefer a meaningful ephemeral expression; fall back to wellbeing when the
     * turn face is absent or non-expressive (neutral/thinking).
     */
    fun resolveSpeechEmotion(
        wellbeing: RobotEmotion,
        ephemeral: RobotEmotion?,
    ): RobotEmotion {
        if (ephemeral == null) return wellbeing
        if (ephemeral == RobotEmotion.NEUTRAL || ephemeral == RobotEmotion.THINKING) {
            return if (wellbeing in STRONG_WELLBEING) wellbeing else ephemeral
        }
        return ephemeral
    }

    /**
     * Deltas must be audible on stock Android TTS (Google/Samsung often swallow ±5%).
     * Still capped so the voice colors mood without becoming a cartoon.
     */
    fun derive(emotion: RobotEmotion, intensity: Float): TtsProsody {
        val i = intensity.coerceIn(0.35f, 1f) // floor so mild moods still move the needle
        return when (emotion) {
            RobotEmotion.HAPPY, RobotEmotion.LOVING ->
                TtsProsody(pitch = 1f + 0.22f * i, rate = 1f + 0.14f * i)
            RobotEmotion.SURPRISED ->
                TtsProsody(pitch = 1f + 0.20f * i, rate = 1f + 0.12f * i)
            RobotEmotion.SAD ->
                TtsProsody(pitch = 1f - 0.22f * i, rate = 1f - 0.24f * i)
            RobotEmotion.ANGRY ->
                TtsProsody(pitch = 1f - 0.10f * i, rate = 1f + 0.20f * i)
            RobotEmotion.BORED ->
                TtsProsody(pitch = 1f - 0.14f * i, rate = 1f - 0.20f * i)
            RobotEmotion.DROWSY, RobotEmotion.SLEEPING ->
                TtsProsody(pitch = 1f - 0.16f * i, rate = 1f - 0.28f * i)
            RobotEmotion.CONFUSED ->
                TtsProsody(pitch = 1f + 0.06f * i, rate = 1f - 0.12f * i)
            else -> TtsProsody.NEUTRAL
        }
    }
}
