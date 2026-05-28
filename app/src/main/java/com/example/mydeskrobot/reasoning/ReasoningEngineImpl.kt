package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.IntermediateResponse
import com.example.mydeskrobot.reasoning.model.ReasoningResult
import com.example.mydeskrobot.reasoning.tool.ToolExecutor
import com.example.mydeskrobot.reasoning.tool.toSystemPromptSection

/**
 * Implementation of [ReasoningEngine].
 * Platform-agnostic: no Android dependencies.
 * 
 * This is the main entry point for Layer 1 (Robot UI) to interact with
 * Layer 2 (Reasoning Module).
 */
class ReasoningEngineImpl(
    private val llmClient: LlmClient,
    private val toolExecutor: ToolExecutor,
    private val baseSystemPrompt: String,
    private val memoryContextProvider: MemoryContextProvider? = null,
    maxChainSteps: Int = 10,
) : ReasoningEngine {
    
    private val responseParser = LlmResponseParser()
    
    private val orchestrator = ToolChainOrchestrator(
        llmClient = llmClient,
        toolExecutor = toolExecutor,
        responseParser = responseParser,
        systemPrompt = buildFullSystemPrompt(),
        maxChainSteps = maxChainSteps,
    )
    
    override suspend fun processUserInput(
        userText: String,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        if (userText.isBlank()) {
            return ReasoningResult.Error("Empty input")
        }
        refreshSystemPrompt(userText)
        return orchestrator.processUserInput(userText, onIntermediateResponse)
    }
    
    override suspend fun processUserInputWithImage(
        userText: String,
        imageBytes: ByteArray,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        if (imageBytes.isEmpty()) {
            return ReasoningResult.Error("Empty image")
        }
        refreshSystemPrompt(userText)
        return orchestrator.processUserInputWithImage(userText, imageBytes, onIntermediateResponse)
    }
    
    override suspend fun continueAfterConfirmation(
        confirmed: Boolean,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        return orchestrator.continueAfterConfirmation(confirmed, onIntermediateResponse)
    }
    
    override fun reset() {
        orchestrator.reset()
    }
    
    override fun isConfigured(): Boolean {
        return llmClient.isConfigured()
    }
    
    private fun buildFullSystemPrompt(): String {
        val toolSection = toolExecutor.getAvailableTools().toSystemPromptSection()
        
        return if (toolSection.isNotBlank()) {
            "$baseSystemPrompt\n\n$toolSection"
        } else {
            baseSystemPrompt
        }
    }

    private suspend fun refreshSystemPrompt(userText: String) {
        val toolPrompt = buildFullSystemPrompt()
        val memoryContext = memoryContextProvider?.buildContextFor(userText).orEmpty()
        val finalPrompt = if (memoryContext.isBlank()) {
            toolPrompt
        } else {
            "$toolPrompt\n\n$memoryContext"
        }
        orchestrator.updateSystemPrompt(finalPrompt)
    }
}
