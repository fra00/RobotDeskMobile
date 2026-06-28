package com.example.mydeskrobot.domain.heartbeat

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Rules for heartbeat MICRO ticks (no LLM): mood/body/eyes only.
 */
object HeartbeatMicroTickPolicy {

    const val MIN_IDLE_MINUTES = 15L

    fun shouldRun(
        moodEmotion: RobotEmotion?,
        idleMinutes: Long,
        presenceAllows: Boolean,
        voiceSessionActive: Boolean,
    ): Boolean {
        if (!voiceSessionActive) return false
        if (!presenceAllows) return false
        if (idleMinutes < MIN_IDLE_MINUTES) return false
        return moodEmotion == RobotEmotion.BORED ||
            moodEmotion == RobotEmotion.DROWSY ||
            idleMinutes >= 20
    }
}
