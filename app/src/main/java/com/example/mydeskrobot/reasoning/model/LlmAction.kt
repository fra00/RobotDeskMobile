package com.example.mydeskrobot.reasoning.model

/**
 * Action requested by the LLM in its response.
 * Sealed class for type-safe handling of different action types.
 */
sealed class LlmAction {
    /** No action required, just speak the reply */
    data object None : LlmAction()
    
    /** Execute one or more tools */
    data class ToolCall(
        val tools: List<ToolInvocation>,
        val chainStatus: ChainStatus = ChainStatus.IN_PROGRESS,
        val parallel: Boolean = false,
    ) : LlmAction()
    
    /** Tool execution requires user confirmation first */
    data class ConfirmRequired(
        val tool: ToolInvocation,
        val confirmPrompt: String,
    ) : LlmAction()
}
