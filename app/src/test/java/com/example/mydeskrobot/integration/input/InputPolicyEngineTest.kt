package com.example.mydeskrobot.integration.input

import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.presentation.conversation.ConversationPhase
import com.example.mydeskrobot.presentation.conversation.ConversationUiState
import com.example.mydeskrobot.reasoning.model.InputPriority
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputPolicyEngineTest {

    @Test
    fun `canProcessNow returns false when mic is off`() {
        val state = createState(
            isHotwordListeningActive = false,
            phase = ConversationPhase.WaitingForHotword,
        )

        assertFalse(InputPolicyEngine.canProcessNow(InputPriority.BLOCKING, state))
        assertFalse(InputPolicyEngine.canProcessNow(InputPriority.DEFERRED, state))
    }

    @Test
    fun `canProcessNow returns true for BLOCKING when mic is on`() {
        val stateWaiting = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.WaitingForHotword,
        )
        val stateSpeaking = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.Speaking,
        )
        val stateThinking = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.Thinking,
        )

        assertTrue(InputPolicyEngine.canProcessNow(InputPriority.BLOCKING, stateWaiting))
        assertTrue(InputPolicyEngine.canProcessNow(InputPriority.BLOCKING, stateSpeaking))
        assertTrue(InputPolicyEngine.canProcessNow(InputPriority.BLOCKING, stateThinking))
    }

    @Test
    fun `canProcessNow returns true for DEFERRED when idle`() {
        val stateWaiting = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.WaitingForHotword,
        )
        val stateListening = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.ActiveListening,
        )

        assertTrue(InputPolicyEngine.canProcessNow(InputPriority.DEFERRED, stateWaiting))
        assertTrue(InputPolicyEngine.canProcessNow(InputPriority.DEFERRED, stateListening))
    }

    @Test
    fun `canProcessNow returns false for DEFERRED when assistant turn in progress`() {
        val stateSpeaking = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.Speaking,
        )
        val stateThinking = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.Thinking,
        )
        val stateCapturing = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.CapturingImage,
        )

        assertFalse(InputPolicyEngine.canProcessNow(InputPriority.DEFERRED, stateSpeaking))
        assertFalse(InputPolicyEngine.canProcessNow(InputPriority.DEFERRED, stateThinking))
        assertFalse(InputPolicyEngine.canProcessNow(InputPriority.DEFERRED, stateCapturing))
    }

    @Test
    fun `canAcceptInput returns false when mic is off`() {
        val state = createState(
            isHotwordListeningActive = false,
            phase = ConversationPhase.WaitingForHotword,
        )

        assertFalse(InputPolicyEngine.canAcceptInput(state))
    }

    @Test
    fun `canAcceptInput returns true when mic is on`() {
        val state = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.WaitingForHotword,
        )

        assertTrue(InputPolicyEngine.canAcceptInput(state))
    }

    @Test
    fun `shouldSuppressForNightMode suppresses DEFERRED in night mode`() {
        val state = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.WaitingForHotword,
            isNightMode = true,
        )

        assertTrue(InputPolicyEngine.shouldSuppressForNightMode(state, InputPriority.DEFERRED))
    }

    @Test
    fun `shouldSuppressForNightMode does not suppress BLOCKING in night mode`() {
        val state = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.WaitingForHotword,
            isNightMode = true,
        )

        assertFalse(InputPolicyEngine.shouldSuppressForNightMode(state, InputPriority.BLOCKING))
    }

    @Test
    fun `shouldSuppressForNightMode does not suppress when not night mode`() {
        val state = createState(
            isHotwordListeningActive = true,
            phase = ConversationPhase.WaitingForHotword,
            isNightMode = false,
        )

        assertFalse(InputPolicyEngine.shouldSuppressForNightMode(state, InputPriority.DEFERRED))
        assertFalse(InputPolicyEngine.shouldSuppressForNightMode(state, InputPriority.BLOCKING))
    }

    private fun createState(
        isHotwordListeningActive: Boolean,
        phase: ConversationPhase,
        isNightMode: Boolean = false,
    ): ConversationUiState {
        return ConversationUiState(
            isHotwordListeningActive = isHotwordListeningActive,
            phase = phase,
            isNightMode = isNightMode,
            emotion = RobotEmotion.NEUTRAL,
            statusMessage = "",
            wakePhraseHint = "",
            exitPhraseHint = "",
        )
    }
}
