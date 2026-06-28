package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.presentation.conversation.ConversationPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyExpressionContextTest {

    @Test
    fun allowsEphemeralGesture_duringSpeaking() {
        val context = BodyExpressionContext(
            phase = ConversationPhase.Speaking,
            isLlmBusy = false,
            isVisionBusy = false,
            isBodyHardwareBusy = false,
        )
        assertTrue(context.allowsEphemeralGesture())
    }

    @Test
    fun blocksEphemeralGesture_duringThinking() {
        val context = BodyExpressionContext(
            phase = ConversationPhase.Thinking,
            isLlmBusy = true,
            isVisionBusy = false,
            isBodyHardwareBusy = false,
        )
        assertFalse(context.allowsEphemeralGesture())
    }

    @Test
    fun blocksMoodExpression_whenBodyHardwareBusy() {
        val context = BodyExpressionContext(
            phase = ConversationPhase.Speaking,
            isLlmBusy = false,
            isVisionBusy = false,
            isBodyHardwareBusy = true,
        )
        assertFalse(context.allowsMoodExpression(MoodReason.POSITIVE_INTERACTION))
    }

    @Test
    fun eyePoke_allowedEvenWhenNotStandby() {
        val context = BodyExpressionContext(
            phase = ConversationPhase.ActiveListening,
            isLlmBusy = false,
            isVisionBusy = false,
            isBodyHardwareBusy = false,
        )
        assertTrue(context.allowsMoodExpression(MoodReason.EYE_POKE))
    }

    @Test
    fun allowsSpeakingMicroMoves_onlyDuringSpeaking() {
        val speaking = BodyExpressionContext(
            phase = ConversationPhase.Speaking,
            isLlmBusy = true,
            isVisionBusy = false,
            isBodyHardwareBusy = false,
        )
        assertTrue(speaking.allowsSpeakingMicroMoves())

        val listening = BodyExpressionContext(
            phase = ConversationPhase.ActiveListening,
            isLlmBusy = false,
            isVisionBusy = false,
            isBodyHardwareBusy = false,
        )
        assertFalse(listening.allowsSpeakingMicroMoves())
    }
}
