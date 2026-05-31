package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Evaluates mood transitions based on triggers and current state.
 * Pure Kotlin, no Android dependencies.
 *
 * Transition rules (from AUTONOMOUS_AGENT_VISION.md):
 * - Standby > 30 min: NEUTRAL → BORED (0.3)
 * - Standby > 90 min: BORED → DROWSY (0.5)
 * - Reminder urgente < 15 min: → ANXIOUS (not available, use SURPRISED as proxy)
 * - Interazione positiva recente: → HAPPY (0.4), decay 20 min
 * - Notte: → SLEEPING (1.0)
 */
class MoodEngine(
    private val config: MoodConfig = MoodConfig(),
) {
    /**
     * Evaluate a trigger against the current mood and return new mood if transition occurs.
     */
    fun evaluate(current: RobotMood, trigger: MoodTrigger, now: Long = System.currentTimeMillis()): RobotMood? {
        return when (trigger) {
            is MoodTrigger.NightMode -> transitionToSleeping(current, now)
            is MoodTrigger.DayMode -> transitionFromNight(current, now)
            is MoodTrigger.IdleTime -> evaluateIdleTransition(current, trigger.minutes, now)
            is MoodTrigger.UserInteraction -> transitionToHappyOnInteraction(current, now)
            is MoodTrigger.LlmEmotion -> transitionFromLlmEmotion(current, trigger.emotion, now)
            is MoodTrigger.ReminderSoon -> evaluateReminderUrgency(current, trigger.minutesUntil, now)
            is MoodTrigger.HeartbeatSuppressed -> null
        }
    }

    /**
     * Check if current mood should decay back to neutral based on time.
     */
    fun checkDecay(current: RobotMood, now: Long = System.currentTimeMillis()): RobotMood? {
        val minutesInMood = current.durationMinutes(now)

        return when (current.baseEmotion) {
            RobotEmotion.HAPPY -> {
                if (minutesInMood >= config.happyDecayMinutes) {
                    RobotMood(
                        baseEmotion = RobotEmotion.NEUTRAL,
                        intensity = 0.5f,
                        since = now,
                        reason = null,
                    )
                } else null
            }
            RobotEmotion.SURPRISED -> {
                if (minutesInMood >= config.reminderUrgentMinutes) {
                    RobotMood(
                        baseEmotion = RobotEmotion.NEUTRAL,
                        intensity = 0.5f,
                        since = now,
                        reason = null,
                    )
                } else null
            }
            else -> null
        }
    }

    private fun transitionToSleeping(current: RobotMood, now: Long): RobotMood? {
        if (current.baseEmotion == RobotEmotion.SLEEPING) return null
        return RobotMood(
            baseEmotion = RobotEmotion.SLEEPING,
            intensity = 1.0f,
            since = now,
            reason = MoodReason.NIGHT_TIME,
        )
    }

    private fun transitionFromNight(current: RobotMood, now: Long): RobotMood? {
        if (current.baseEmotion != RobotEmotion.SLEEPING &&
            current.baseEmotion != RobotEmotion.DROWSY
        ) {
            return null
        }
        return RobotMood(
            baseEmotion = RobotEmotion.NEUTRAL,
            intensity = 0.5f,
            since = now,
            reason = null,
        )
    }

    private fun evaluateIdleTransition(current: RobotMood, idleMinutes: Long, now: Long): RobotMood? {
        if (current.baseEmotion == RobotEmotion.SLEEPING) return null
        if (current.baseEmotion == RobotEmotion.HAPPY) return null

        return when {
            idleMinutes >= config.boredToDrowsyMinutes && current.baseEmotion == RobotEmotion.BORED -> {
                RobotMood(
                    baseEmotion = RobotEmotion.DROWSY,
                    intensity = 0.5f,
                    since = now,
                    reason = MoodReason.IDLE_VERY_LONG,
                )
            }
            idleMinutes >= config.idleToBoredMinutes && current.baseEmotion == RobotEmotion.NEUTRAL -> {
                RobotMood(
                    baseEmotion = RobotEmotion.BORED,
                    intensity = 0.3f,
                    since = now,
                    reason = MoodReason.IDLE_LONG,
                )
            }
            else -> null
        }
    }

    private fun transitionToHappyOnInteraction(current: RobotMood, now: Long): RobotMood? {
        if (current.baseEmotion == RobotEmotion.HAPPY && current.intensity >= 0.4f) {
            return current.copy(since = now)
        }
        return RobotMood(
            baseEmotion = RobotEmotion.HAPPY,
            intensity = 0.4f,
            since = now,
            reason = MoodReason.USER_RETURNED,
        )
    }

    private fun transitionFromLlmEmotion(current: RobotMood, emotion: RobotEmotion, now: Long): RobotMood? {
        if (emotion == current.baseEmotion) return null
        if (emotion == RobotEmotion.LISTENING || emotion == RobotEmotion.SPEAKING) return null
        if (emotion == RobotEmotion.THINKING) return null

        val intensity = when (emotion) {
            RobotEmotion.HAPPY -> 0.6f
            RobotEmotion.SURPRISED -> 0.5f
            RobotEmotion.ANGRY -> 0.7f
            RobotEmotion.SAD -> 0.5f
            RobotEmotion.CONFUSED -> 0.4f
            RobotEmotion.LOVING -> 0.7f
            else -> 0.5f
        }

        val reason = when (emotion) {
            RobotEmotion.HAPPY, RobotEmotion.LOVING -> MoodReason.POSITIVE_INTERACTION
            RobotEmotion.ANGRY, RobotEmotion.SAD -> MoodReason.NEGATIVE_INTERACTION
            else -> null
        }

        return RobotMood(
            baseEmotion = emotion,
            intensity = intensity,
            since = now,
            reason = reason,
        )
    }

    private fun evaluateReminderUrgency(current: RobotMood, minutesUntil: Int, now: Long): RobotMood? {
        if (minutesUntil > config.reminderUrgentMinutes) return null
        if (current.baseEmotion == RobotEmotion.SLEEPING) return null
        if (current.reason == MoodReason.REMINDER_URGENT) return null

        return RobotMood(
            baseEmotion = RobotEmotion.SURPRISED,
            intensity = 0.6f,
            since = now,
            reason = MoodReason.REMINDER_URGENT,
        )
    }
}
