package com.example.mydeskrobot.reasoning.llm

/**
 * Raw response from an LLM provider.
 * Platform-agnostic representation.
 */
data class LlmResponse(
    /** Raw text content from the LLM */
    val content: String,
    
    /** Model that generated the response (for logging) */
    val model: String? = null,
    
    /** Reason why generation stopped (e.g., "stop", "length") */
    val finishReason: String? = null,
    
    /** Token usage for monitoring */
    val usage: TokenUsage? = null,
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)
