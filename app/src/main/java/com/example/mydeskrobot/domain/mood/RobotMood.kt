package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Autonomous emotional wellbeing of the robot (persistent valence + derived standby expression).
 * LLM [emotion] JSON is handled separately as [EphemeralExpression] (not stored here).
 */
data class RobotMood(
    val valence: Float,
    val baseline: Float,
    val baseEmotion: RobotEmotion,
    val intensity: Float,
    val since: Long,
    val reason: MoodReason?,
    val recentDeltas: List<MoodDelta> = emptyList(),
) {
    init {
        require(intensity in 0f..1f) { "Intensity must be in [0, 1]" }
        require(valence in MoodValenceConfig.VALENCE_MIN..MoodValenceConfig.VALENCE_MAX) {
            "Valence must be in [${MoodValenceConfig.VALENCE_MIN}, ${MoodValenceConfig.VALENCE_MAX}]"
        }
    }

    fun durationMinutes(now: Long = System.currentTimeMillis()): Long =
        (now - since) / 60_000L

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
            )
        }
    }
}

enum class MoodReason {
    IDLE_LONG,
    IDLE_VERY_LONG,
    NIGHT_TIME,
    POSITIVE_INTERACTION,
    NEGATIVE_INTERACTION,
    TASK_COMPLETED,
    EYE_POKE,
    USER_APOLOGY,
    REMINDER_URGENT,
    USER_RETURNED,
    HEARTBEAT_SUPPRESSED,
}

sealed interface MoodTrigger {
    data class IdleTime(val minutes: Long) : MoodTrigger
    data object NightMode : MoodTrigger
    data object DayMode : MoodTrigger
    data object PositiveInteraction : MoodTrigger
    data object NegativeInteraction : MoodTrigger
    data object TaskCompletedUseful : MoodTrigger
    data object UserApology : MoodTrigger
    data class EyePoked(val tier: Int, val count: Int) : MoodTrigger
    data class ReminderSoon(val minutesUntil: Int) : MoodTrigger
    data object HeartbeatSuppressed : MoodTrigger
}

data class MoodConfig(
    val idleToBoredMinutes: Int = 30,
    val boredToDrowsyMinutes: Int = 90,
    val happyDecayMinutes: Int = 20,
    val eyePokeAnnoyanceDecayMinutes: Int = 8,
    val reminderUrgentMinutes: Int = 15,
)
