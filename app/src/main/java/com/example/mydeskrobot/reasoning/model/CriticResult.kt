package com.example.mydeskrobot.reasoning.model

/**
 * Result of the optional heartbeat critic LLM pass (HIGH-sensitivity domains).
 */
sealed class CriticResult {
    data class Approve(val text: String) : CriticResult()
    data class Modify(val text: String) : CriticResult()
    data object Block : CriticResult()
    data class Failed(val message: String, val fallbackText: String) : CriticResult()
}
