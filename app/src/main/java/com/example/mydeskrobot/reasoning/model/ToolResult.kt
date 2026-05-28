package com.example.mydeskrobot.reasoning.model

/**
 * Result of a tool execution.
 * Platform-agnostic representation.
 */
sealed class ToolResult {
    /** Tool executed successfully with data */
    data class Success(
        val data: Map<String, Any?>,
    ) : ToolResult()
    
    /** Tool execution failed */
    data class Error(
        val message: String,
        val code: String? = null,
        val recoverable: Boolean = true,
    ) : ToolResult()
    
    /** Tool requires user confirmation before proceeding */
    data class NeedsConfirmation(
        val prompt: String,
    ) : ToolResult()
    
    /** Tool returned binary data (e.g., image) */
    data class BinaryData(
        val data: ByteArray,
        val mimeType: String,
        val metadata: Map<String, Any?> = emptyMap(),
    ) : ToolResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BinaryData) return false
            return data.contentEquals(other.data) && mimeType == other.mimeType
        }
        
        override fun hashCode(): Int {
            return 31 * data.contentHashCode() + mimeType.hashCode()
        }
    }
}
