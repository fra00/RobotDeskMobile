package com.example.mydeskrobot.reasoning.memory

sealed class RecallPlanFailure {
    data object NotConfigured : RecallPlanFailure()
    data class LlmError(val message: String) : RecallPlanFailure()
    data object EmptyOutput : RecallPlanFailure()
    data class ParseError(val detail: String) : RecallPlanFailure()
}
