package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.model.ConversationMessage
import com.example.mydeskrobot.reasoning.model.ToolResult

/**
 * Manages conversation history for multi-turn LLM interactions.
 * Platform-agnostic: no Android dependencies.
 */
class ConversationHistory(
    private val maxMessages: Int = 50,
) {
    private val messages = mutableListOf<ConversationMessage>()
    
    fun addUserMessage(content: String) {
        if (content.isBlank()) return
        messages.add(ConversationMessage.User(content))
        trimIfNeeded()
    }
    
    fun addAssistantMessage(response: ParsedLlmResponse) {
        val content = buildString {
            append(response.text)
            if (response.action != com.example.mydeskrobot.reasoning.model.LlmAction.None) {
                append("\n[Action requested]")
            }
        }
        if (content.isNotBlank()) {
            messages.add(ConversationMessage.Assistant(content))
            trimIfNeeded()
        }
    }
    
    fun addAssistantRawMessage(content: String) {
        if (content.isBlank()) return
        messages.add(ConversationMessage.Assistant(content))
        trimIfNeeded()
    }
    
    fun addToolResult(toolName: String, result: ToolResult) {
        messages.add(ConversationMessage.ToolResultMessage(toolName, result))
        trimIfNeeded()
    }

    fun addSystemInput(formattedContent: String) {
        if (formattedContent.isBlank()) return
        messages.add(ConversationMessage.SystemInputMessage(formattedContent))
        trimIfNeeded()
    }
    
    fun toMessages(): List<ConversationMessage> = messages.toList()
    
    fun clear() {
        messages.clear()
    }
    
    fun isEmpty(): Boolean = messages.isEmpty()
    
    fun size(): Int = messages.size
    
    private fun trimIfNeeded() {
        while (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }
}
