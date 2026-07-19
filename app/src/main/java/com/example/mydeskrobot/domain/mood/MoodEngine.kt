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
            is MoodTrigger.HotwordListeningIdle ->
                evaluateHotwordListeningIdle(current, trigger.minutes, now)
            is MoodTrigger.VoiceTurnPresence ->
                applyDelta(
                    current = current,
                    delta = trigger.delta,
                    event = "presenza_vocale",
                    reason = MoodReason.VOICE_TURN_PRESENCE,
                    now = now,
                    guard = { trigger.delta <= 0f || !isAnnoyedFromEyePoke(current) },
                )
            is MoodTrigger.ValenceDelta ->
                applyDelta(
                    current = current,
                    delta = trigger.delta,
                    event = trigger.event,
                    reason = trigger.reason,
                    now = now,
                    guard = { trigger.delta < 0f || !isAnnoyedFromEyePoke(current) },
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
            is MoodTrigger.LlmEmotion -> evaluateLlmEmotion(current, trigger.emotion, trigger.tier, now)
            is MoodTrigger.UserApology -> transitionOnApology(current, now)
            is MoodTrigger.EyePoked -> transitionOnEyePoke(current, trigger.tier, now)
        }
    }

    fun checkDecay(current: RobotMood, now: Long = System.currentTimeMillis()): RobotMood? {
        val minutesInMood = current.durationMinutes(now)

        return when {
            current.reason == MoodReason.EYE_POKE &&
                isAnnoyedFromEyePoke(current) &&
                minutesInMood >= config.eyePokeAnnoyanceDecayMinutes ->
                driftTowardBaseline(current, now, clearReason = true)

            canDriftTowardBaseline(current) &&
                current.valence > current.baseline &&
                minutesInMood >= config.happyDecayMinutes ->
                driftTowardBaseline(current, now, clearReason = true)

            canDriftTowardBaseline(current) &&
                current.valence < current.baseline &&
                minutesInMood >= config.sadDecayMinutes ->
                driftTowardBaseline(current, now, clearReason = true)

            else -> null
        }
    }

    /**
     * Generic drift applies to event-driven valence (LLM emotion, task, presence, fatigue).
     * Excluded: night (forced sleeping), idle (managed by the idle loop), poke (own rule).
     */
    private fun canDriftTowardBaseline(current: RobotMood): Boolean = when (current.reason) {
        MoodReason.NIGHT_TIME,
        MoodReason.IDLE_LONG,
        MoodReason.IDLE_VERY_LONG,
        MoodReason.IDLE_LISTENING,
        MoodReason.EYE_POKE,
        -> false
        else -> true
    }

    private fun evaluateLlmEmotion(
        current: RobotMood,
        emotion: RobotEmotion,
        tier: LlmEmotionValenceTier,
        now: Long,
    ): RobotMood? {
        val delta = LlmEmotionValenceMapper.valenceDelta(emotion, tier) ?: return null
        if (kotlin.math.abs(delta) < 0.001f) return null
        return applyDelta(
            current = current,
            delta = delta,
            event = "llm_emotion_${emotion.name.lowercase()}",
            reason = MoodReason.LLM_EXPRESSION,
            now = now,
            guard = { delta < 0f || !isAnnoyedFromEyePoke(current) },
        )
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

    private fun evaluateHotwordListeningIdle(
        current: RobotMood,
        idleMinutes: Long,
        now: Long,
    ): RobotMood? {
        if (current.baseEmotion == RobotEmotion.SLEEPING) return null
        if (idleMinutes < config.hotwordIdleToBoredMinutes) return null
        if (current.reason == MoodReason.IDLE_LISTENING) return null
        if (isAnnoyedFromEyePoke(current)) return null

        return applyDelta(
            current = current,
            delta = valenceConfig.hotwordIdleBored,
            event = "hotword_idle",
            reason = MoodReason.IDLE_LISTENING,
            now = now,
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

    private fun isAnnoyedFromEyePoke(current: RobotMood): Boolean =
        current.reason == MoodReason.EYE_POKE ||
            (current.reason == MoodReason.USER_APOLOGY &&
                (current.baseEmotion == RobotEmotion.ANGRY ||
                    current.baseEmotion == RobotEmotion.CONFUSED))
}
