package com.example.mydeskrobot.reasoning.model

/**
 * Intermediate response during tool chain execution.
 * The caller (Robot UI) decides how to handle this (TTS, UI update, etc.)
 */
data class IntermediateResponse(
    /** Text to potentially speak or display */
    val text: String,
    
    /** Emotion suggested by the LLM (e.g., "happy", "thinking") */
    val emotion: String? = null,
    
    /** True if a tool is currently executing */
    val isToolExecuting: Boolean = false,
    
    /** Name of the tool being executed (if isToolExecuting) */
    val toolName: String? = null,
    
    /** LLM's confidence that the response is worth speaking (for heartbeat filtering) */
    val speakConfidence: Double? = null,

    /** When true, Robot UI must not TTS this reply (chain still in_progress). */
    val suppressIntermediateSpeech: Boolean = false,
)
