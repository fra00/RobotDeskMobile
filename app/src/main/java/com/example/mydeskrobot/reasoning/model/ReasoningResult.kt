package com.example.mydeskrobot.reasoning.model

/**
 * Final result of a reasoning/tool chain execution.
 */
sealed class ReasoningResult {
    /** Reasoning completed successfully */
    data class Success(
        val finalText: String,
        val emotion: String? = null,
        val speakConfidence: Double? = null,
    ) : ReasoningResult()
    
    /** Reasoning requires user confirmation to proceed */
    data class NeedsConfirmation(
        val prompt: String,
        val pendingAction: suspend (confirmed: Boolean) -> ReasoningResult,
    ) : ReasoningResult()
    
    /** Reasoning failed with an error */
    data class Error(
        val message: String,
        val recoverable: Boolean = true,
    ) : ReasoningResult()
    
    /** Reached maximum chain steps without completing */
    data class MaxStepsReached(
        val lastText: String,
        val stepsExecuted: Int,
    ) : ReasoningResult()
}
