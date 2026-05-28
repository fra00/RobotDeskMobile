package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.IntermediateResponse
import com.example.mydeskrobot.reasoning.model.ReasoningResult
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope
import com.example.mydeskrobot.reasoning.tool.ToolExecutor
import com.example.mydeskrobot.reasoning.tool.toSystemPromptSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    companion object {
        private const val DATETIME_PLACEHOLDER = "{{CURRENT_DATETIME}}"
    }
    
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

    override suspend fun processSystemInput(
        envelope: SystemInputEnvelope,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        if (envelope.formattedForLlm.isBlank()) {
            return ReasoningResult.Error("Empty system input")
        }
        refreshSystemPromptForSystemInput()
        return orchestrator.processSystemInput(envelope, onIntermediateResponse)
    }
    
    override fun reset() {
        orchestrator.reset()
    }
    
    override fun isConfigured(): Boolean {
        return llmClient.isConfigured()
    }
    
    private fun buildFullSystemPrompt(): String {
        val toolSection = toolExecutor.getAvailableTools().toSystemPromptSection()
        val promptWithDateTime = baseSystemPrompt.replace(
            DATETIME_PLACEHOLDER, 
            getCurrentDateTimeString()
        )
        
        return if (toolSection.isNotBlank()) {
            "$promptWithDateTime\n\n$toolSection"
        } else {
            promptWithDateTime
        }
    }

    private fun getCurrentDateTimeString(): String {
        val dateFormat = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN)
        return dateFormat.format(Date())
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

    private fun refreshSystemPromptForSystemInput() {
        val toolPrompt = buildFullSystemPrompt()
        orchestrator.updateSystemPrompt(toolPrompt)
    }
}
