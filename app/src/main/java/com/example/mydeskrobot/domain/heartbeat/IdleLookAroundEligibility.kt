package com.example.mydeskrobot.domain.heartbeat

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Pure gates for silent idle look-around (ex heartbeat MICRO tick).
 * Does **not** use [com.example.mydeskrobot.domain.proactive.ProactiveSpeakGate] —
 * look-around never speaks and must not be blocked by proactive speak budget.
 */
object IdleLookAroundEligibility {

    fun isWithinActiveWindow(startHour: Int, endHour: Int, currentHour: Int): Boolean =
        if (startHour <= endHour) {
            currentHour in startHour until endHour
        } else {
            currentHour >= startHour || currentHour < endHour
        }

    fun cooldownElapsed(
        lastLookAroundAtMs: Long?,
        intervalMinutes: Int,
        nowMs: Long,
    ): Boolean {
        if (lastLookAroundAtMs == null || lastLookAroundAtMs <= 0L) return true
        val elapsed = nowMs - lastLookAroundAtMs
        return elapsed >= intervalMinutes.coerceAtLeast(1) * 60_000L
    }

    /**
     * @return true when eyes/body idle look-around may run.
     */
    fun shouldRun(
        microTickEnabled: Boolean,
        voiceSessionActive: Boolean,
        withinActiveWindow: Boolean,
        isNightMode: Boolean,
        robotContextSilent: Boolean,
        presenceAllows: Boolean,
        moodEmotion: RobotEmotion?,
        idleMinutes: Long,
        lastLookAroundAtMs: Long?,
        intervalMinutes: Int,
        nowMs: Long,
    ): Boolean {
        if (!microTickEnabled) return false
        if (!withinActiveWindow) return false
        if (isNightMode) return false
        if (robotContextSilent) return false
        if (!cooldownElapsed(lastLookAroundAtMs, intervalMinutes, nowMs)) return false
        return HeartbeatMicroTickPolicy.shouldRun(
            moodEmotion = moodEmotion,
            idleMinutes = idleMinutes,
            presenceAllows = presenceAllows,
            voiceSessionActive = voiceSessionActive,
        )
    }

    fun suggestBodyLookAround(moodEmotion: RobotEmotion?): Boolean =
        moodEmotion == RobotEmotion.BORED || moodEmotion == RobotEmotion.DROWSY
}
