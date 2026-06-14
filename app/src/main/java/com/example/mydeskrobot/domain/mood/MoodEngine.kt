package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import kotlin.math.abs

/**
 * Evaluates persistent wellbeing (valence) transitions. LLM emotions are ephemeral only.
 */
class MoodEngine(
    private val config: MoodConfig = MoodConfig(),
    private val valenceConfig: MoodValenceConfig = MoodValenceConfig(),
) {

    fun evaluate(current: RobotMood, trigger: MoodTrigger, now: Long = System.currentTimeMillis()): RobotMood? {
        return when (trigger) {
            is MoodTrigger.NightMode -> transitionToSleeping(current, now)
            is MoodTrigger.DayMode -> transitionFromNight(current, now)
            is MoodTrigger.IdleTime -> evaluateIdleTransition(current, trigger.minutes, now)
            is MoodTrigger.PositiveInteraction ->
                applyDelta(
                    current = current,
                    delta = valenceConfig.positiveInteraction,
                    event = "interazione_positiva",
                    reason = MoodReason.POSITIVE_INTERACTION,
                    now = now,
                    guard = { !isAnnoyedFromEyePoke(current) },
                )
            is MoodTrigger.NegativeInteraction ->
                applyDelta(
                    current = current,
                    delta = valenceConfig.negativeInteraction,
                    event = "interazione_negativa",
                    reason = MoodReason.NEGATIVE_INTERACTION,
                    now = now,
                )
            is MoodTrigger.TaskCompletedUseful ->
                applyDelta(
                    current = current,
                    delta = valenceConfig.taskCompleted,
                    event = "task_completato",
                    reason = MoodReason.TASK_COMPLETED,
                    now = now,
                    guard = { !isAnnoyedFromEyePoke(current) },
                )
            is MoodTrigger.UserApology -> transitionOnApology(current, now)
            is MoodTrigger.EyePoked -> transitionOnEyePoke(current, trigger.tier, now)
            is MoodTrigger.ReminderSoon -> evaluateReminderUrgency(current, trigger.minutesUntil, now)
            is MoodTrigger.HeartbeatSuppressed -> null
        }
    }

    fun checkDecay(current: RobotMood, now: Long = System.currentTimeMillis()): RobotMood? {
        val minutesInMood = current.durationMinutes(now)

        return when {
            current.reason == MoodReason.POSITIVE_INTERACTION &&
                minutesInMood >= config.happyDecayMinutes &&
                current.valence > current.baseline ->
                driftTowardBaseline(current, now)

            current.reason == MoodReason.REMINDER_URGENT &&
                minutesInMood >= config.reminderUrgentMinutes ->
                RobotMood.fromValence(
                    valence = current.valence,
                    baseline = current.baseline,
                    since = now,
                    reason = null,
                    recentDeltas = current.recentDeltas,
                )

            current.reason == MoodReason.EYE_POKE &&
                isAnnoyedFromEyePoke(current) &&
                minutesInMood >= config.eyePokeAnnoyanceDecayMinutes ->
                driftTowardBaseline(current, now, clearReason = true)

            else -> null
        }
    }

    private fun applyDelta(
        current: RobotMood,
        delta: Float,
        event: String,
        reason: MoodReason?,
        now: Long,
        guard: () -> Boolean = { true },
    ): RobotMood? {
        if (!guard()) return null

        val newValence = (current.valence + delta)
            .coerceIn(valenceConfig.valenceMin, valenceConfig.valenceMax)

        if (abs(newValence - current.valence) < 0.001f && reason == current.reason) {
            return current.copy(since = now, reason = reason)
        }

        val newDeltas = (current.recentDeltas + MoodDelta(event, delta, now))
            .takeLast(valenceConfig.maxRecentDeltas)

        return RobotMood.fromValence(
            valence = newValence,
            baseline = current.baseline,
            since = now,
            reason = reason,
            recentDeltas = newDeltas,
        )
    }

    private fun driftTowardBaseline(
        current: RobotMood,
        now: Long,
        clearReason: Boolean = false,
    ): RobotMood {
        val step = valenceConfig.decayTowardBaseline
        val target = current.baseline
        val newValence = when {
            current.valence > target -> (current.valence - step).coerceAtLeast(target)
            current.valence < target -> (current.valence + step).coerceAtMost(target)
            else -> current.valence
        }
        return RobotMood.fromValence(
            valence = newValence,
            baseline = current.baseline,
            since = now,
            reason = if (clearReason && abs(newValence - target) < 0.02f) null else current.reason,
            recentDeltas = current.recentDeltas,
        )
    }

    private fun transitionToSleeping(current: RobotMood, now: Long): RobotMood? {
        if (current.reason == MoodReason.NIGHT_TIME &&
            current.baseEmotion == RobotEmotion.SLEEPING
        ) {
            return null
        }
        return RobotMood.fromValence(
            valence = current.valence,
            baseline = current.baseline,
            since = now,
            reason = MoodReason.NIGHT_TIME,
            recentDeltas = current.recentDeltas,
            forceEmotion = RobotEmotion.SLEEPING,
            forceIntensity = 1.0f,
        )
    }

    private fun transitionFromNight(current: RobotMood, now: Long): RobotMood? {
        if (current.baseEmotion != RobotEmotion.SLEEPING &&
            current.baseEmotion != RobotEmotion.DROWSY
        ) {
            return null
        }
        return RobotMood.fromValence(
            valence = current.valence,
            baseline = current.baseline,
            since = now,
            reason = null,
            recentDeltas = current.recentDeltas,
        )
    }

    private fun evaluateIdleTransition(current: RobotMood, idleMinutes: Long, now: Long): RobotMood? {
        if (current.baseEmotion == RobotEmotion.SLEEPING) return null
        if (current.valence >= 0.35f) return null
        if (isAnnoyedFromEyePoke(current)) return null

        return when {
            idleMinutes >= config.boredToDrowsyMinutes &&
                (current.baseEmotion == RobotEmotion.BORED || current.reason == MoodReason.IDLE_LONG) ->
                applyDelta(
                    current = current,
                    delta = valenceConfig.idleDrowsy,
                    event = "idle_molto_lungo",
                    reason = MoodReason.IDLE_VERY_LONG,
                    now = now,
                )

            idleMinutes >= config.idleToBoredMinutes &&
                current.baseEmotion == RobotEmotion.NEUTRAL &&
                current.reason == null ->
                applyDelta(
                    current = current,
                    delta = valenceConfig.idleBored,
                    event = "idle_lungo",
                    reason = MoodReason.IDLE_LONG,
                    now = now,
                )

            else -> null
        }
    }

    private fun transitionOnEyePoke(current: RobotMood, tier: Int, now: Long): RobotMood? {
        val delta = when {
            tier >= 3 -> valenceConfig.eyePokeTier3
            tier >= 2 -> valenceConfig.eyePokeTier2
            else -> valenceConfig.eyePokeTier1
        }
        return applyDelta(
            current = current,
            delta = delta,
            event = "poke_occhi",
            reason = MoodReason.EYE_POKE,
            now = now,
        )
    }

    private fun transitionOnApology(current: RobotMood, now: Long): RobotMood? {
        if (!isAnnoyedFromEyePoke(current)) return null
        return applyDelta(
            current = current,
            delta = valenceConfig.userApology,
            event = "scusa_utente",
            reason = MoodReason.USER_APOLOGY,
            now = now,
        )
    }

    private fun evaluateReminderUrgency(current: RobotMood, minutesUntil: Int, now: Long): RobotMood? {
        if (minutesUntil > config.reminderUrgentMinutes) return null
        if (current.baseEmotion == RobotEmotion.SLEEPING) return null
        if (current.reason == MoodReason.REMINDER_URGENT) return null

        return RobotMood.fromValence(
            valence = current.valence,
            baseline = current.baseline,
            since = now,
            reason = MoodReason.REMINDER_URGENT,
            recentDeltas = current.recentDeltas,
            forceEmotion = RobotEmotion.SURPRISED,
            forceIntensity = 0.6f,
        )
    }

    private fun isAnnoyedFromEyePoke(current: RobotMood): Boolean =
        current.reason == MoodReason.EYE_POKE ||
            (current.reason == MoodReason.USER_APOLOGY &&
                (current.baseEmotion == RobotEmotion.ANGRY ||
                    current.baseEmotion == RobotEmotion.CONFUSED))
}
