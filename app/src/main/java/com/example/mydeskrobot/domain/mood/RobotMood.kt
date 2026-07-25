package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Autonomous emotional wellbeing of the robot (persistent valence + derived standby expression).
 * LLM [emotion] on a completed turn updates [EphemeralExpression] and may shift persistent valence
 * via [LlmEmotionValenceMapper] (see [MoodTrigger.LlmEmotion]).
 *
 * [lastDecayAtMs] drives inexorable drift toward [baseline]; event deltas must not reset it.
 */
data class RobotMood(
    val valence: Float,
    val baseline: Float,
    val baseEmotion: RobotEmotion,
    val intensity: Float,
    val since: Long,
    val reason: MoodReason?,
    val recentDeltas: List<MoodDelta> = emptyList(),
    val lastDecayAtMs: Long = since,
) {
    init {
        require(intensity in 0f..1f) { "Intensity must be in [0, 1]" }
        require(valence in MoodValenceConfig.VALENCE_MIN..MoodValenceConfig.VALENCE_MAX) {
            "Valence must be in [${MoodValenceConfig.VALENCE_MIN}, ${MoodValenceConfig.VALENCE_MAX}]"
        }
    }

    fun durationMinutes(now: Long = System.currentTimeMillis()): Long =
        (now - since) / 60_000L

    fun minutesSinceLastDecay(now: Long = System.currentTimeMillis()): Long =
        (now - lastDecayAtMs) / 60_000L

    companion object {
        val NEUTRAL: RobotMood = fromValence(
            valence = MoodValenceConfig.DEFAULT_BASELINE,
            since = System.currentTimeMillis(),
            reason = null,
        )

        fun fromValence(
            valence: Float,
            baseline: Float = MoodValenceConfig.DEFAULT_BASELINE,
            since: Long = System.currentTimeMillis(),
            reason: MoodReason? = null,
            recentDeltas: List<MoodDelta> = emptyList(),
            forceEmotion: RobotEmotion? = null,
            forceIntensity: Float? = null,
            lastDecayAtMs: Long = since,
        ): RobotMood {
            val clamped = valence.coerceIn(MoodValenceConfig.VALENCE_MIN, MoodValenceConfig.VALENCE_MAX)
            val derived = if (forceEmotion != null) {
                DerivedEmotion(forceEmotion, forceIntensity ?: 0.5f)
            } else {
                MoodValenceMapper.derive(clamped, reason)
            }
            return RobotMood(
                valence = clamped,
                baseline = baseline,
                baseEmotion = derived.emotion,
                intensity = derived.intensity,
                since = since,
                reason = reason,
                recentDeltas = recentDeltas,
                lastDecayAtMs = lastDecayAtMs,
            )
        }

        /** Migrates legacy emotion-only snapshots to valence. */
        fun fromLegacy(
            emotion: RobotEmotion,
            intensity: Float,
            since: Long,
            reason: MoodReason?,
        ): RobotMood {
            val valence = when (emotion) {
                RobotEmotion.HAPPY, RobotEmotion.LOVING -> 0.45f
                RobotEmotion.ANGRY -> -0.32f
                RobotEmotion.SAD -> -0.28f
                RobotEmotion.BORED -> -0.18f
                RobotEmotion.CONFUSED -> -0.12f
                RobotEmotion.DROWSY -> -0.15f
                RobotEmotion.SURPRISED -> 0.2f
                RobotEmotion.SLEEPING -> MoodValenceConfig.DEFAULT_BASELINE
                else -> MoodValenceConfig.DEFAULT_BASELINE
            }
            return fromValence(
                valence = valence,
                since = since,
                reason = reason,
                forceEmotion = emotion,
                forceIntensity = intensity,
                lastDecayAtMs = since,
            )
        }
    }
}

enum class MoodReason {
    IDLE_LONG,
    IDLE_VERY_LONG,
    IDLE_LISTENING,
    CONVERSATION_FATIGUE,
    VOICE_TURN_PRESENCE,
    NIGHT_TIME,
    TASK_COMPLETED,
    EYE_POKE,
    USER_APOLOGY,
    LLM_EXPRESSION,
}

sealed interface MoodTrigger {
    data class IdleTime(val minutes: Long) : MoodTrigger
    data class HotwordListeningIdle(val minutes: Long) : MoodTrigger
    data class VoiceTurnPresence(val delta: Float) : MoodTrigger
    data class ValenceDelta(
        val delta: Float,
        val event: String,
        val reason: MoodReason?,
    ) : MoodTrigger
    data object NightMode : MoodTrigger
    data object DayMode : MoodTrigger
    data object TaskCompletedUseful : MoodTrigger
    data class LlmEmotion(
        val emotion: RobotEmotion,
        val tier: LlmEmotionValenceTier = LlmEmotionValenceTier.FULL,
    ) : MoodTrigger
    data object UserApology : MoodTrigger
    data class EyePoked(val tier: Int, val count: Int) : MoodTrigger
}

data class MoodConfig(
    val idleToBoredMinutes: Int = DEFAULT_IDLE_TO_BORED_MINUTES,
    val hotwordIdleToBoredMinutes: Int = DEFAULT_HOTWORD_IDLE_TO_BORED_MINUTES,
    val boredToDrowsyMinutes: Int = 90,
    /** Minutes between inexorable drift steps when valence is above baseline. */
    val happyDecayMinutes: Int = 5,
    /** Minutes between inexorable drift steps when valence is below baseline. */
    val sadDecayMinutes: Int = 6,
    val eyePokeAnnoyanceDecayMinutes: Int = 5,
    val burstTurnCount: Int = DEFAULT_BURST_TURN_COUNT,
    val burstWindowMinutes: Int = DEFAULT_BURST_WINDOW_MINUTES,
    val repeatedPhraseThreshold: Int = DEFAULT_REPEATED_PHRASE_THRESHOLD,
    val shortPhraseWordLimit: Int = DEFAULT_SHORT_PHRASE_WORD_LIMIT,
    val positiveBoostCap: Int = DEFAULT_POSITIVE_BOOST_CAP,
) {
    companion object {
        const val DEFAULT_IDLE_TO_BORED_MINUTES = 30
        const val DEFAULT_HOTWORD_IDLE_TO_BORED_MINUTES = 10
        const val DEFAULT_BURST_TURN_COUNT = 4
        const val DEFAULT_BURST_WINDOW_MINUTES = 3
        const val DEFAULT_REPEATED_PHRASE_THRESHOLD = 3
        const val DEFAULT_SHORT_PHRASE_WORD_LIMIT = 4
        const val DEFAULT_POSITIVE_BOOST_CAP = 3
    }
}
