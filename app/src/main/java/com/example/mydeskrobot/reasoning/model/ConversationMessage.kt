package com.example.mydeskrobot.reasoning.model

/**
 * Represents a message in the conversation history.
 * Platform-agnostic, used for multi-turn LLM interactions.
 */
sealed class ConversationMessage {
    abstract val role: String
    abstract val content: String
    
    /** Message from the user */
    data class User(
        override val content: String,
    ) : ConversationMessage() {
        override val role: String = "user"
    }
    
    /** Message from the assistant (LLM) */
    data class Assistant(
        override val content: String,
    ) : ConversationMessage() {
        override val role: String = "assistant"
    }
    
    /** Tool result injected into conversation */
    data class ToolResultMessage(
        val toolName: String,
        val result: ToolResult,
    ) : ConversationMessage() {
        override val role: String = "user"
        override val content: String
            get() = formatToolResult()
        
        private fun formatToolResult(): String {
            val resultText = when (result) {
                is ToolResult.Success -> result.data.toString()
                is ToolResult.Error -> "ERROR: ${result.message}"
                is ToolResult.NeedsConfirmation -> "NEEDS_CONFIRMATION: ${result.prompt}"
                is ToolResult.BinaryData -> "[Binary data: ${result.mimeType}, ${result.data.size} bytes]"
            }
            return "[TOOL_RESULT: $toolName]\n$resultText"
        }
    }
}
