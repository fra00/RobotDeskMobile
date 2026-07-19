package com.example.mydeskrobot.domain.proactive

import java.util.concurrent.TimeUnit

/**
 * Pure Kotlin rules for "user present enough" before proactive speak.
 */
object UserPresencePolicy {

    fun hasRecentUserTurn(lastUserTurnMs: Long?, now: Long, windowMinutes: Int): Boolean {
        if (lastUserTurnMs == null) return false
        val windowMs = TimeUnit.MINUTES.toMillis(windowMinutes.toLong())
        return now - lastUserTurnMs <= windowMs
    }

    /**
     * Predictivity: recent voice turn (default 10 min) OR body locate.
     * Recent turn is checked first (cheap); body locate is a fallback.
     */
    suspend fun predictivityPresentEnough(
        lastUserTurnMs: Long?,
        bodyConfigured: Boolean,
        bodyReachable: Boolean,
        locateUser: suspend () -> Boolean,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (hasRecentUserTurn(
                lastUserTurnMs,
                now,
                ProactivityConstants.PREDICTIVITY_PRESENCE_MINUTES,
            )
        ) {
            return true
        }
        if (bodyConfigured && bodyReachable && locateUser()) {
            return true
        }
        return false
    }

    /**
     * Wellness presence (speak / start DOMAIN_SCORE and VISUAL_ORDER):
     * - With body: try locate first; if not found, require a recent user turn.
     * - Without body: recent user turn only.
     *
     * [presenceWindowMinutes] is the interaction fallback (default 5) — separate from
     * the post-dialog idle buffer used for scheduling.
     */
    suspend fun wellnessPresentEnough(
        lastUserTurnMs: Long?,
        bodyConfigured: Boolean,
        bodyReachable: Boolean,
        locateUser: suspend () -> Boolean,
        presenceWindowMinutes: Int = ProactivityConstants.WELLNESS_PRESENCE_MINUTES,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val recentTurn = hasRecentUserTurn(lastUserTurnMs, now, presenceWindowMinutes)
        if (bodyConfigured && bodyReachable) {
            if (locateUser()) return true
            return recentTurn
        }
        return recentTurn
    }
}
