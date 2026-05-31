package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Autonomous emotional state of the robot.
 * Unlike [RobotEmotion] which is set by LLM or phase changes,
 * [RobotMood] evolves independently based on time and context.
 */
data class RobotMood(
    val baseEmotion: RobotEmotion,
    val intensity: Float,
    val since: Long,
    val reason: MoodReason?,
) {
    init {
        require(intensity in 0f..1f) { "Intensity must be in [0, 1]" }
    }

    fun durationMinutes(now: Long = System.currentTimeMillis()): Long =
        (now - since) / 60_000L

    companion object {
        val NEUTRAL = RobotMood(
            baseEmotion = RobotEmotion.NEUTRAL,
            intensity = 0.5f,
            since = System.currentTimeMillis(),
            reason = null,
        )
    }
}

/**
 * Why the robot is in a certain mood.
 */
enum class MoodReason {
    IDLE_LONG,
    IDLE_VERY_LONG,
    NIGHT_TIME,
    POSITIVE_INTERACTION,
    NEGATIVE_INTERACTION,
    REMINDER_URGENT,
    USER_RETURNED,
    HEARTBEAT_SUPPRESSED,
}

/**
 * Trigger that can cause a mood transition.
 */
sealed interface MoodTrigger {
    data class IdleTime(val minutes: Long) : MoodTrigger
    data object NightMode : MoodTrigger
    data object DayMode : MoodTrigger
    data object UserInteraction : MoodTrigger
    data class LlmEmotion(val emotion: RobotEmotion) : MoodTrigger
    data class ReminderSoon(val minutesUntil: Int) : MoodTrigger
    data object HeartbeatSuppressed : MoodTrigger
}

/**
 * Configuration for mood transitions.
 */
data class MoodConfig(
    val idleToBoredMinutes: Int = 30,
    val boredToDrowsyMinutes: Int = 90,
    val happyDecayMinutes: Int = 20,
    val reminderUrgentMinutes: Int = 15,
)
