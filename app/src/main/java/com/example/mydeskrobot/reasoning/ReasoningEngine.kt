package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.model.CriticResult
import com.example.mydeskrobot.reasoning.model.IntermediateResponse
import com.example.mydeskrobot.reasoning.model.ReasoningResult
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope

/**
 * Main interface for the Reasoning Module.
 * This is the contract between Layer 1 (Robot UI) and Layer 2 (Reasoning).
 * 
 * Platform-agnostic: the caller (Robot UI) handles TTS, UI, etc.
 */
interface ReasoningEngine {
    /**
     * Process user text input and execute any required tool chains.
     * 
     * @param userText User's spoken/typed input
     * @param onIntermediateResponse Callback for intermediate responses (TTS, UI updates)
     * @return Final reasoning result
     */
    suspend fun processUserInput(
        userText: String,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit = {},
    ): ReasoningResult
    
    /**
     * Process user input with an attached image.
     * 
     * @param userText User's spoken/typed input
     * @param imageBytes JPEG image bytes
     * @param onIntermediateResponse Callback for intermediate responses
     * @return Final reasoning result
     */
    suspend fun processUserInputWithImage(
        userText: String,
        imageBytes: ByteArray,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit = {},
    ): ReasoningResult
    
    /**
     * Continue a conversation after user confirmation.
     * 
     * @param confirmed Whether the user confirmed the action
     * @param onIntermediateResponse Callback for intermediate responses
     * @return Final reasoning result
     */
    suspend fun continueAfterConfirmation(
        confirmed: Boolean,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit = {},
    ): ReasoningResult

    /**
     * Clears a pending confirmation without executing the tool (e.g. user changed topic).
     */
    fun cancelPendingConfirmation()

    /**
     * Process a system input (notification, hardware button, sensor).
     * Unlike user input, this comes from the system rather than voice.
     * 
     * @param envelope The system input envelope with formatted content
     * @param onIntermediateResponse Callback for intermediate responses
     * @return Final reasoning result
     */
    suspend fun processSystemInput(
        envelope: SystemInputEnvelope,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit = {},
    ): ReasoningResult

    /**
     * Optional second LLM pass for HIGH-sensitivity heartbeat domains.
     * Reviews proposed spoken text (tone, repetition) — no tools.
     */
    suspend fun processCriticPass(
        proposal: String,
        domainId: String,
        domainName: String?,
        recentInterventions: List<String>,
    ): CriticResult
    
    /**
     * Reset conversation history.
     * Call this when starting a new conversation session.
     */
    fun reset()
    
    /**
     * Check if the engine is properly configured.
     */
    fun isConfigured(): Boolean
}
