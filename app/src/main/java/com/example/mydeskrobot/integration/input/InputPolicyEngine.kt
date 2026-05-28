package com.example.mydeskrobot.integration.input

import com.example.mydeskrobot.presentation.conversation.ConversationPhase
import com.example.mydeskrobot.presentation.conversation.ConversationUiState
import com.example.mydeskrobot.reasoning.model.InputPriority

/**
 * Decides whether a system input can be processed immediately or must be deferred.
 */
object InputPolicyEngine {

    /**
     * Check if an input with the given priority can be processed now.
     *
     * Rules:
     * - Mic must be active (includes WaitingForHotword)
     * - BLOCKING inputs are always processed immediately
     * - DEFERRED inputs wait until robot is idle (not Thinking/Speaking/CapturingImage)
     *
     * @param priority The input's priority level
     * @param uiState Current UI state
     * @return true if the input should be processed now, false to defer
     */
    fun canProcessNow(priority: InputPriority, uiState: ConversationUiState): Boolean {
        if (!uiState.isHotwordListeningActive) {
            return false
        }

        if (priority == InputPriority.BLOCKING) {
            return true
        }

        return !isAssistantTurnInProgress(uiState.phase)
    }

    /**
     * Check if the robot can accept any input at all.
     * Used to decide whether to drop inputs entirely vs. queue them.
     */
    fun canAcceptInput(uiState: ConversationUiState): Boolean {
        return uiState.isHotwordListeningActive
    }

    /**
     * Check if night mode should suppress non-critical inputs.
     */
    fun shouldSuppressForNightMode(uiState: ConversationUiState, priority: InputPriority): Boolean {
        if (!uiState.isNightMode) return false
        return priority == InputPriority.DEFERRED
    }

    private fun isAssistantTurnInProgress(phase: ConversationPhase): Boolean {
        return phase is ConversationPhase.Thinking ||
            phase is ConversationPhase.CapturingImage ||
            phase is ConversationPhase.Speaking
    }
}
