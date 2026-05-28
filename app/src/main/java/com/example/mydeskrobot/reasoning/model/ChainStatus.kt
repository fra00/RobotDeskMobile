package com.example.mydeskrobot.reasoning.model

/**
 * Status of a tool chain execution.
 * Used by the LLM to signal progress and termination.
 */
enum class ChainStatus {
    /** Chain is still executing, more steps expected */
    IN_PROGRESS,
    
    /** Chain completed successfully */
    COMPLETE,
    
    /** Chain failed and cannot continue */
    FAILED,
}
