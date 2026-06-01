package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Evaluates mood transitions based on triggers and current state.
 * Pure Kotlin, no Android dependencies.
 */
class MoodEngine(
    private val config: MoodConfig = MoodConfig(),
) {

    companion object {
        private val CALMING_EMOTIONS = setOf(
            RobotEmotion.NEUTRAL,
            RobotEmotion.CONFUSED,
            RobotEmotion.SAD,
            RobotEmotion.BORED,
            RobotEmotion.DROWSY,
        )
    }
    fun evaluate(current: RobotMood, trigger: MoodTrigger, now: Long = System.currentTimeMillis()): RobotMood? {
        return when (trigger) {
            is MoodTrigger.NightMode -> transitionToSleeping(current, now)
            is MoodTrigger.DayMode -> transitionFromNight(current, now)
            is MoodTrigger.IdleTime -> evaluateIdleTransition(current, trigger.minutes, now)
            is MoodTrigger.PositiveInteraction -> transitionToHappyOnPositiveInteraction(current, now)
            is MoodTrigger.UserApology -> transitionOnApology(current, now)
            is MoodTrigger.EyePoked -> transitionOnEyePoke(current, trigger.tier, now)
            is MoodTrigger.AssistantDeclaredEmotion ->
                transitionFromAssistantDeclaredEmotion(current, trigger.emotion, now)
            is MoodTrigger.LlmEmotion -> transitionFromLlmEmotion(current, trigger.emotion, now)
            is MoodTrigger.ReminderSoon -> evaluateReminderUrgency(current, trigger.minutesUntil, now)
            is MoodTrigger.HeartbeatSuppressed -> null
        }
    }

    fun checkDecay(current: RobotMood, now: Long = System.currentTimeMillis()): RobotMood? {
        val minutesInMood = current.durationMinutes(now)

        return when {
            current.baseEmotion == RobotEmotion.HAPPY &&
                minutesInMood >= config.happyDecayMinutes -> {
                neutralMood(now)
            }
            current.baseEmotion == RobotEmotion.SURPRISED &&
                minutesInMood >= config.reminderUrgentMinutes -> {
                neutralMood(now)
            }
            current.reason == MoodReason.EYE_POKE &&
                (current.baseEmotion == RobotEmotion.ANGRY || current.baseEmotion == RobotEmotion.CONFUSED) &&
                minutesInMood >= config.eyePokeAnnoyanceDecayMinutes -> {
                neutralMood(now)
            }
            else -> null
        }
    }

    private fun neutralMood(now: Long) = RobotMood(
        baseEmotion = RobotEmotion.NEUTRAL,
        intensity = 0.5f,
        since = now,
        reason = null,
    )

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
        return neutralMood(now)
    }

    private fun evaluateIdleTransition(current: RobotMood, idleMinutes: Long, now: Long): RobotMood? {
        if (current.baseEmotion == RobotEmotion.SLEEPING) return null
        if (current.baseEmotion == RobotEmotion.HAPPY) return null
        if (isAnnoyedFromEyePoke(current)) return null

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

    private fun transitionOnEyePoke(current: RobotMood, tier: Int, now: Long): RobotMood? {
        val (emotion, intensity) = when {
            tier >= 3 -> RobotEmotion.ANGRY to 0.85f
            tier >= 2 -> RobotEmotion.ANGRY to 0.65f
            else -> RobotEmotion.CONFUSED to 0.4f
        }
        if (current.baseEmotion == emotion &&
            current.reason == MoodReason.EYE_POKE &&
            current.intensity >= intensity
        ) {
            return current.copy(since = now)
        }
        return RobotMood(
            baseEmotion = emotion,
            intensity = intensity,
            since = now,
            reason = MoodReason.EYE_POKE,
        )
    }

    private fun transitionOnApology(current: RobotMood, now: Long): RobotMood? {
        if (!isAnnoyedFromEyePoke(current)) return null

        return when {
            current.baseEmotion == RobotEmotion.ANGRY && current.intensity > 0.5f -> {
                current.copy(
                    baseEmotion = RobotEmotion.CONFUSED,
                    intensity = 0.45f,
                    since = now,
                    reason = MoodReason.USER_APOLOGY,
                )
            }
            else -> RobotMood(
                baseEmotion = RobotEmotion.NEUTRAL,
                intensity = 0.5f,
                since = now,
                reason = MoodReason.USER_APOLOGY,
            )
        }
    }

    private fun transitionToHappyOnPositiveInteraction(current: RobotMood, now: Long): RobotMood? {
        if (isAnnoyedFromEyePoke(current)) return null

        if (current.baseEmotion == RobotEmotion.HAPPY && current.intensity >= 0.4f) {
            return current.copy(since = now, reason = MoodReason.POSITIVE_INTERACTION)
        }
        return RobotMood(
            baseEmotion = RobotEmotion.HAPPY,
            intensity = 0.4f,
            since = now,
            reason = MoodReason.POSITIVE_INTERACTION,
        )
    }

    private fun isAnnoyedFromEyePoke(current: RobotMood): Boolean =
        current.reason == MoodReason.EYE_POKE &&
            (current.baseEmotion == RobotEmotion.ANGRY || current.baseEmotion == RobotEmotion.CONFUSED)

    /**
     * When the assistant declares an emotion in JSON, align persistent mood so eyes match speech.
     * Prefer calming transitions after annoyance; avoid spurious happy while still annoyed from poke.
     */
    private fun transitionFromAssistantDeclaredEmotion(
        current: RobotMood,
        emotion: RobotEmotion,
        now: Long,
    ): RobotMood? {
        if (emotion == RobotEmotion.LISTENING ||
            emotion == RobotEmotion.SPEAKING ||
            emotion == RobotEmotion.THINKING
        ) {
            return null
        }

        val annoyed = isAnnoyedFromEyePoke(current) || current.reason == MoodReason.USER_APOLOGY
        val calming = emotion in CALMING_EMOTIONS

        if (annoyed && calming) {
            val intensity = when (emotion) {
                RobotEmotion.NEUTRAL -> 0.5f
                RobotEmotion.CONFUSED -> 0.4f
                RobotEmotion.SAD -> 0.45f
                RobotEmotion.BORED -> 0.35f
                RobotEmotion.DROWSY -> 0.5f
                else -> 0.5f
            }
            val reason = when {
                emotion == RobotEmotion.NEUTRAL -> null
                current.reason == MoodReason.USER_APOLOGY -> MoodReason.USER_APOLOGY
                else -> current.reason
            }
            return RobotMood(
                baseEmotion = emotion,
                intensity = intensity,
                since = now,
                reason = reason,
            )
        }

        if (current.baseEmotion == RobotEmotion.ANGRY &&
            emotion == RobotEmotion.NEUTRAL
        ) {
            return neutralMood(now)
        }

        if (isAnnoyedFromEyePoke(current) && emotion == RobotEmotion.HAPPY) {
            return null
        }

        return transitionFromLlmEmotion(current, emotion, now)
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
