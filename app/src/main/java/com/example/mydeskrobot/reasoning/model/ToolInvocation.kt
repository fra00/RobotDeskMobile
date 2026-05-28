package com.example.mydeskrobot.reasoning.model

/**
 * Represents a tool invocation request from the LLM.
 * Platform-agnostic: the Reasoning Module doesn't know where tools execute.
 */
data class ToolInvocation(
    /** Tool name (e.g., "get_weather", "take_photo", "move_head") */
    val name: String,
    
    /** Tool parameters as key-value pairs */
    val params: Map<String, Any?> = emptyMap(),
    
    /** If true, the LLM expects the result to continue reasoning */
    val awaitResult: Boolean = true,
    
    /** Optional purpose/intent for logging and debugging */
    val purpose: String? = null,
)
