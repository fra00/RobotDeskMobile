package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.presentation.conversation.ConversationPhase

/**
 * Runtime guards for mood-driven and ephemeral body expression.
 */
data class BodyExpressionContext(
    val phase: ConversationPhase,
    val isLlmBusy: Boolean,
    val isVisionBusy: Boolean,
    val isBodyHardwareBusy: Boolean,
) {
    val isStandby: Boolean
        get() = phase is ConversationPhase.WaitingForHotword

    fun allowsMoodExpression(reason: MoodReason?): Boolean {
        if (isVisionBusy || isBodyHardwareBusy) return false
        return when (reason) {
            MoodReason.EYE_POKE -> true
            MoodReason.USER_APOLOGY -> !isLlmBusy
            else -> allowsConversationGesture() && !isLlmBusy
        }
    }

    fun allowsEphemeralGesture(): Boolean = allowsConversationGesture()

    fun allowsMicroTick(): Boolean {
        if (isVisionBusy || isBodyHardwareBusy || isLlmBusy) return false
        return isStandby
    }

    fun allowsSpeakingMicroMoves(): Boolean {
        if (isVisionBusy || isBodyHardwareBusy) return false
        return phase is ConversationPhase.Speaking
    }

    private fun allowsConversationGesture(): Boolean {
        if (isVisionBusy || isBodyHardwareBusy || isLlmBusy) return false
        return when (phase) {
            is ConversationPhase.Speaking,
            is ConversationPhase.ActiveListening,
            is ConversationPhase.WaitingForHotword,
            -> true
            else -> false
        }
    }
}
