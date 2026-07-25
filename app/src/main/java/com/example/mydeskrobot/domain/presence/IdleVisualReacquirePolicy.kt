package com.example.mydeskrobot.domain.presence

/**
 * Pure rules for silent idle visual re-acquire (mic on, no active dialog turn).
 */
object IdleVisualReacquirePolicy {

    const val NO_FACE_THRESHOLD_MS = 5L * 60_000L
    const val ATTEMPT_COOLDOWN_MS = 10L * 60_000L

    fun shouldScan(
        nowMs: Long,
        sessionStartedAtMs: Long,
        lastFaceSeenAtMs: Long?,
        lastIdleAttemptAtMs: Long?,
        isWaitingForHotword: Boolean,
        suppressForRobotContext: Boolean,
    ): Boolean {
        if (!isWaitingForHotword) return false
        if (suppressForRobotContext) return false

        lastIdleAttemptAtMs?.let { last ->
            if (nowMs - last < ATTEMPT_COOLDOWN_MS) return false
        }

        val referenceFaceAt = lastFaceSeenAtMs ?: sessionStartedAtMs
        return nowMs - referenceFaceAt >= NO_FACE_THRESHOLD_MS
    }
}
