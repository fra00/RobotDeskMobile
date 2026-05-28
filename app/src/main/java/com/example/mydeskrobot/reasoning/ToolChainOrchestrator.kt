package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ChainStatus
import com.example.mydeskrobot.reasoning.model.IntermediateResponse
import com.example.mydeskrobot.reasoning.model.LlmAction
import com.example.mydeskrobot.reasoning.model.ReasoningResult
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrates multi-turn tool chains with the LLM.
 * Platform-agnostic: no Android dependencies.
 * 
 * Flow:
 * 1. Send user message to LLM
 * 2. Parse response for actions
 * 3. If tool_call: execute tools, add results to history, loop back to 1
 * 4. If none: return success
 * 5. If confirm_required: return needs confirmation
 */
class ToolChainOrchestrator(
    private val llmClient: LlmClient,
    private val toolExecutor: ToolExecutor,
    private val responseParser: LlmResponseParser,
    private var systemPrompt: String,
    private val maxChainSteps: Int = 10,
    private val timeoutMs: Long = 60_000,
) {
    fun updateSystemPrompt(value: String) {
        systemPrompt = value
    }

    private val conversationHistory = ConversationHistory()
    private var pendingConfirmation: PendingConfirmation? = null
    /** Image returned by a tool (e.g. take_photo) to forward to the next LLM call. */
    private var pendingImageBytes: ByteArray? = null
    
    suspend fun processUserInput(
        userText: String,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        conversationHistory.addUserMessage(userText)
        return executeChain(onIntermediateResponse)
    }
    
    suspend fun processUserInputWithImage(
        userText: String,
        imageBytes: ByteArray,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        conversationHistory.addUserMessage(userText)
        return executeChainWithImage(imageBytes, onIntermediateResponse)
    }
    
    suspend fun continueAfterConfirmation(
        confirmed: Boolean,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        val pending = pendingConfirmation
        pendingConfirmation = null
        
        if (pending == null) {
            return ReasoningResult.Error("No pending confirmation")
        }
        
        if (!confirmed) {
            conversationHistory.addUserMessage("No, annulla")
            return ReasoningResult.Success("Ok, annullato.", "neutral")
        }
        
        conversationHistory.addUserMessage("Sì, procedi")
        
        val toolResult = toolExecutor.execute(pending.tool)
        conversationHistory.addToolResult(pending.tool.name, toolResult)
        
        if (!pending.tool.awaitResult) {
            return ReasoningResult.Success(pending.lastText, pending.lastEmotion)
        }
        
        return executeChain(onIntermediateResponse)
    }
    
    fun reset() {
        conversationHistory.clear()
        pendingConfirmation = null
        pendingImageBytes = null
    }
    
    private suspend fun executeChain(
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        var step = 0
        var lastResponse: ParsedLlmResponse? = null
        
        while (step < maxChainSteps) {
            step++
            
            val imageForThisTurn = pendingImageBytes
            pendingImageBytes = null
            
            val llmResult = if (imageForThisTurn != null) {
                llmClient.chatWithImage(
                    messages = conversationHistory.toMessages(),
                    systemPrompt = systemPrompt,
                    imageBytes = imageForThisTurn,
                )
            } else {
                llmClient.chat(
                    messages = conversationHistory.toMessages(),
                    systemPrompt = systemPrompt,
                )
            }
            
            if (llmResult.isFailure) {
                val error = llmResult.exceptionOrNull()?.message ?: "LLM communication error"
                return ReasoningResult.Error(error)
            }
            
            val parsed = try {
                responseParser.parse(llmResult.getOrThrow().content)
            } catch (e: Exception) {
                return ReasoningResult.Error("Failed to parse LLM response: ${e.message}")
            }
            
            lastResponse = parsed
            conversationHistory.addAssistantRawMessage(llmResult.getOrThrow().content)
            
            when (val action = parsed.action) {
                is LlmAction.None -> {
                    return ReasoningResult.Success(parsed.text, parsed.emotion)
                }
                
                is LlmAction.ToolCall -> {
                    val alreadySpokenText = parsed.text.isNotBlank()
                    if (alreadySpokenText) {
                        onIntermediateResponse(
                            IntermediateResponse(
                                text = parsed.text,
                                emotion = parsed.emotion,
                                isToolExecuting = false,
                            )
                        )
                    }
                    val results = executeTools(action, onIntermediateResponse)
                    
                    results.forEach { (tool, result) ->
                        conversationHistory.addToolResult(tool.name, result)
                        captureImageIfPresent(result)
                    }
                    
                    val chainTerminated = action.tools.none { it.awaitResult } ||
                        action.chainStatus == ChainStatus.COMPLETE
                    
                    if (chainTerminated) {
                        // Text already emitted via intermediate; signal Success with empty text
                        // so the caller doesn't re-speak it.
                        val finalText = if (alreadySpokenText) "" else parsed.text
                        return ReasoningResult.Success(finalText, parsed.emotion)
                    }
                }
                
                is LlmAction.ConfirmRequired -> {
                    pendingConfirmation = PendingConfirmation(
                        tool = action.tool,
                        lastText = parsed.text,
                        lastEmotion = parsed.emotion,
                    )
                    return ReasoningResult.NeedsConfirmation(
                        prompt = action.confirmPrompt,
                        pendingAction = { confirmed ->
                            continueAfterConfirmation(confirmed, onIntermediateResponse)
                        }
                    )
                }
            }
        }
        
        return ReasoningResult.MaxStepsReached(
            lastText = lastResponse?.text ?: "",
            stepsExecuted = step,
        )
    }
    
    private suspend fun executeChainWithImage(
        imageBytes: ByteArray,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        val llmResult = llmClient.chatWithImage(
            messages = conversationHistory.toMessages(),
            systemPrompt = systemPrompt,
            imageBytes = imageBytes,
        )
        
        if (llmResult.isFailure) {
            val error = llmResult.exceptionOrNull()?.message ?: "LLM communication error"
            return ReasoningResult.Error(error)
        }
        
        val parsed = try {
            responseParser.parse(llmResult.getOrThrow().content)
        } catch (e: Exception) {
            return ReasoningResult.Error("Failed to parse LLM response: ${e.message}")
        }
        
        conversationHistory.addAssistantRawMessage(llmResult.getOrThrow().content)
        
        return when (val action = parsed.action) {
            is LlmAction.None -> {
                ReasoningResult.Success(parsed.text, parsed.emotion)
            }
            
            is LlmAction.ToolCall -> {
                val alreadySpokenText = parsed.text.isNotBlank()
                if (alreadySpokenText) {
                    onIntermediateResponse(
                        IntermediateResponse(
                            text = parsed.text,
                            emotion = parsed.emotion,
                            isToolExecuting = false,
                        )
                    )
                }
                val results = executeTools(action, onIntermediateResponse)
                results.forEach { (tool, result) ->
                    conversationHistory.addToolResult(tool.name, result)
                    captureImageIfPresent(result)
                }
                
                val chainTerminated = action.tools.none { it.awaitResult } ||
                    action.chainStatus == ChainStatus.COMPLETE
                
                if (chainTerminated) {
                    val finalText = if (alreadySpokenText) "" else parsed.text
                    ReasoningResult.Success(finalText, parsed.emotion)
                } else {
                    executeChain(onIntermediateResponse)
                }
            }
            
            is LlmAction.ConfirmRequired -> {
                pendingConfirmation = PendingConfirmation(
                    tool = action.tool,
                    lastText = parsed.text,
                    lastEmotion = parsed.emotion,
                )
                ReasoningResult.NeedsConfirmation(
                    prompt = action.confirmPrompt,
                    pendingAction = { confirmed ->
                        continueAfterConfirmation(confirmed, onIntermediateResponse)
                    }
                )
            }
        }
    }
    
    private suspend fun executeTools(
        action: LlmAction.ToolCall,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): List<Pair<ToolInvocation, ToolResult>> {
        action.tools.forEach { tool ->
            onIntermediateResponse(
                IntermediateResponse(
                    text = "",
                    emotion = null,
                    isToolExecuting = true,
                    toolName = tool.name,
                )
            )
        }
        
        return if (action.parallel) {
            executeParallel(action.tools)
        } else {
            executeSequential(action.tools)
        }
    }
    
    private suspend fun executeParallel(
        tools: List<ToolInvocation>,
    ): List<Pair<ToolInvocation, ToolResult>> {
        return coroutineScope {
            tools.map { tool ->
                async { tool to toolExecutor.execute(tool) }
            }.awaitAll()
        }
    }
    
    private suspend fun executeSequential(
        tools: List<ToolInvocation>,
    ): List<Pair<ToolInvocation, ToolResult>> {
        return tools.map { tool -> tool to toolExecutor.execute(tool) }
    }
    
    /**
     * If the tool result contains a JPEG image, store it to be sent
     * with the next LLM turn via chatWithImage.
     */
    private fun captureImageIfPresent(result: ToolResult) {
        if (result is ToolResult.BinaryData && result.mimeType.startsWith("image/")) {
            pendingImageBytes = result.data
        }
    }
    
    private data class PendingConfirmation(
        val tool: ToolInvocation,
        val lastText: String,
        val lastEmotion: String?,
    )
}
