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

    suspend fun wellnessPresentEnough(
        lastUserTurnMs: Long?,
        bodyConfigured: Boolean,
        bodyReachable: Boolean,
        locateUser: suspend () -> Boolean,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (hasRecentUserTurn(
                lastUserTurnMs,
                now,
                ProactivityConstants.WELLNESS_PRESENCE_MINUTES,
            )
        ) {
            return true
        }
        if (bodyConfigured && bodyReachable && locateUser()) {
            return true
        }
        return false
    }
}
