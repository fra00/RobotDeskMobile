package com.example.mydeskrobot.reasoning.llm

import com.example.mydeskrobot.reasoning.model.ConversationMessage

/**
 * Interface for LLM providers.
 * Implementations: LmStudioClient, OpenAiClient, ClaudeClient, GeminiClient, etc.
 * 
 * Platform-agnostic: no Android dependencies.
 */
interface LlmClient {
    /**
     * Send a chat request to the LLM.
     * 
     * @param messages Conversation history
     * @param systemPrompt System prompt with instructions
     * @return Raw LLM response or error
     */
    suspend fun chat(
        messages: List<ConversationMessage>,
        systemPrompt: String,
    ): Result<LlmResponse>
    
    /**
     * Send a chat request with an image (multimodal).
     * 
     * @param messages Conversation history
     * @param systemPrompt System prompt with instructions
     * @param imageBytes JPEG image bytes
     * @return Raw LLM response or error
     */
    suspend fun chatWithImage(
        messages: List<ConversationMessage>,
        systemPrompt: String,
        imageBytes: ByteArray,
    ): Result<LlmResponse>
    
    /**
     * Check if the client is properly configured.
     */
    fun isConfigured(): Boolean
}
