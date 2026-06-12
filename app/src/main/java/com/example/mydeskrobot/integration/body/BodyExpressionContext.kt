package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.domain.mood.MoodReason

/**
 * Runtime guards for mood-driven body expression (avoid fighting LLM tool chains).
 */
data class BodyExpressionContext(
    val isStandby: Boolean,
    val isLlmBusy: Boolean,
    val isVisionBusy: Boolean,
) {
    fun allowsExpression(reason: MoodReason?): Boolean {
        if (isVisionBusy) return false
        return when (reason) {
            MoodReason.EYE_POKE -> true
            MoodReason.USER_APOLOGY -> !isLlmBusy
            MoodReason.IDLE_LONG,
            MoodReason.POSITIVE_INTERACTION,
            -> isStandby && !isLlmBusy
            else -> isStandby && !isLlmBusy
        }
    }
}
