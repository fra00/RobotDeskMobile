package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.integration.llm.LlmHttpErrors
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ChainStatus
import com.example.mydeskrobot.reasoning.model.IntermediateResponse
import com.example.mydeskrobot.reasoning.model.LlmAction
import com.example.mydeskrobot.reasoning.model.ReasoningResult
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

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
    private val onBeforeLlmTurn: (suspend (hasPendingImage: Boolean, stepIndex: Int) -> Unit)? = null,
    private val reasoningLogObserver: ReasoningLogObserver = NoOpReasoningLogObserver,
) {
    fun updateSystemPrompt(value: String) {
        systemPrompt = value
    }

    private val conversationHistory = ConversationHistory()
    private var pendingConfirmation: PendingConfirmation? = null
    /** Image returned by a tool (e.g. take_photo) to forward to the next LLM call. */
    private var pendingImageBytes: ByteArray? = null
    private var originalUserText: String = ""

    suspend fun processUserInput(
        userText: String,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        originalUserText = userText
        conversationHistory.addUserMessage(userText)
        reasoningLogObserver.onUserInput(userText)
        return executeChain(onIntermediateResponse)
    }
    
    suspend fun processUserInputWithImage(
        userText: String,
        imageBytes: ByteArray,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        originalUserText = userText
        conversationHistory.addUserMessage(userText)
        return executeChainWithImage(imageBytes, onIntermediateResponse)
    }

    /**
     * Process a system input (notification, hardware button, sensor).
     * Adds the formatted content to conversation history and runs the LLM chain.
     */
    suspend fun processSystemInput(
        envelope: SystemInputEnvelope,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        conversationHistory.addSystemInput(envelope.formattedForLlm)
        reasoningLogObserver.onSystemInput(envelope.formattedForLlm)
        return executeChain(onIntermediateResponse)
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
            return ReasoningResult.Success(
                finalText = "Ok, annullato.",
                emotion = "neutral",
                speakConfidence = 1.0,
            )
        }
        
        conversationHistory.addUserMessage("Sì, procedi")
        
        val toolResult = toolExecutor.execute(pending.tool)
        conversationHistory.addToolResult(pending.tool.name, toolResult)
        
        if (!pending.tool.awaitResult) {
            return ReasoningResult.Success(
                finalText = pending.lastText,
                emotion = pending.lastEmotion,
                speakConfidence = 1.0,
            )
        }
        
        return executeChain(onIntermediateResponse)
    }
    
    fun cancelPendingConfirmation() {
        pendingConfirmation = null
    }

    fun reset() {
        conversationHistory.clear()
        pendingConfirmation = null
        pendingImageBytes = null
        originalUserText = ""
    }

    fun getOriginalUserText(): String = originalUserText
    
    private suspend fun executeChain(
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        var step = 0
        var lastResponse: ParsedLlmResponse? = null
        
        while (step < maxChainSteps) {
            step++

            val imageForThisTurn = pendingImageBytes
            onBeforeLlmTurn?.invoke(imageForThisTurn != null, step)
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
                val error = llmResult.exceptionOrNull()?.let { LlmHttpErrors.formatForLog(it) }
                    ?: "LLM communication error"
                reasoningLogObserver.onOutcome("Error: $error")
                return ReasoningResult.Error(error)
            }
            
            val parsed = try {
                responseParser.parse(llmResult.getOrThrow().content)
            } catch (e: Exception) {
                val message = "Failed to parse LLM response: ${e.message}"
                reasoningLogObserver.onOutcome("Error: $message")
                return ReasoningResult.Error(message)
            }
            
            lastResponse = parsed
            conversationHistory.addAssistantRawMessage(llmResult.getOrThrow().content)
            logLlmStep(step, parsed)

            when (val action = parsed.action) {
                is LlmAction.None -> {
                    reasoningLogObserver.onOutcome("Success (no tool): ${parsed.text.take(200)}")
                    return ReasoningResult.Success(
                        finalText = parsed.text,
                        emotion = parsed.emotion,
                        speakConfidence = parsed.speakConfidence,
                    )
                }
                
                is LlmAction.ToolCall -> {
                    val alreadySpokenText = parsed.text.isNotBlank()
                    if (alreadySpokenText) {
                        onIntermediateResponse(
                            IntermediateResponse(
                                text = parsed.text,
                                emotion = parsed.emotion,
                                isToolExecuting = false,
                                speakConfidence = parsed.speakConfidence,
                                suppressIntermediateSpeech = ChainSpeechPolicy
                                    .suppressIntermediateSpeech(action.chainStatus),
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
                        val toolFailureText = formatToolFailures(results)
                        val finalText = when {
                            toolFailureText != null -> toolFailureText
                            alreadySpokenText -> ""
                            else -> parsed.text
                        }
                        val emotion = if (toolFailureText != null) "confused" else parsed.emotion
                        reasoningLogObserver.onOutcome(
                            if (chainTerminated && action.chainStatus == ChainStatus.COMPLETE) {
                                "Chain complete${if (finalText.isNotBlank()) ": ${finalText.take(200)}" else ""}"
                            } else {
                                "Chain terminated (fire-and-forget or complete)"
                            },
                        )
                        return ReasoningResult.Success(
                            finalText = finalText,
                            emotion = emotion,
                            speakConfidence = parsed.speakConfidence,
                        )
                    }
                }
                
                is LlmAction.ConfirmRequired -> {
                    pendingConfirmation = PendingConfirmation(
                        tool = action.tool,
                        lastText = parsed.text,
                        lastEmotion = parsed.emotion,
                    )
                    reasoningLogObserver.onOutcome("Needs confirmation: ${action.confirmPrompt}")
                    return ReasoningResult.NeedsConfirmation(
                        prompt = action.confirmPrompt,
                        pendingAction = { confirmed ->
                            continueAfterConfirmation(confirmed, onIntermediateResponse)
                        }
                    )
                }
            }
        }
        
        reasoningLogObserver.onOutcome("Max steps reached ($step)")
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
            val error = llmResult.exceptionOrNull()?.let { LlmHttpErrors.formatForLog(it) }
                ?: "LLM communication error"
            return ReasoningResult.Error(error)
        }
        
        val parsed = try {
            responseParser.parse(llmResult.getOrThrow().content)
        } catch (e: Exception) {
            return ReasoningResult.Error("Failed to parse LLM response: ${e.message}")
        }
        
        conversationHistory.addAssistantRawMessage(llmResult.getOrThrow().content)
        logLlmStep(1, parsed)

        return when (val action = parsed.action) {
            is LlmAction.None -> {
                ReasoningResult.Success(
                    finalText = parsed.text,
                    emotion = parsed.emotion,
                    speakConfidence = parsed.speakConfidence,
                )
            }
            
            is LlmAction.ToolCall -> {
                val alreadySpokenText = parsed.text.isNotBlank()
                if (alreadySpokenText) {
                    onIntermediateResponse(
                        IntermediateResponse(
                            text = parsed.text,
                            emotion = parsed.emotion,
                            isToolExecuting = false,
                            speakConfidence = parsed.speakConfidence,
                            suppressIntermediateSpeech = ChainSpeechPolicy
                                .suppressIntermediateSpeech(action.chainStatus),
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
                    val toolFailureText = formatToolFailures(results)
                    val finalText = when {
                        toolFailureText != null -> toolFailureText
                        alreadySpokenText -> ""
                        else -> parsed.text
                    }
                    val emotion = if (toolFailureText != null) "confused" else parsed.emotion
                    ReasoningResult.Success(
                        finalText = finalText,
                        emotion = emotion,
                        speakConfidence = parsed.speakConfidence,
                    )
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
        }.also { results ->
            results.forEach { (tool, result) ->
                reasoningLogObserver.onToolResult(tool, result)
            }
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

    /**
     * Builds a spoken error when a terminating tool chain had failures.
     * Without this, await_result=false tools fail silently after an optimistic intermediate reply.
     */
    private fun formatToolFailures(
        results: List<Pair<ToolInvocation, ToolResult>>,
    ): String? {
        val errors = results.mapNotNull { (tool, result) ->
            (result as? ToolResult.Error)?.let { tool.name to it.message }
        }
        if (errors.isEmpty()) return null

        return errors.joinToString(" ") { (toolName, message) ->
            when (toolName) {
                "open_browser" -> "Non sono riuscito ad aprire il sito. $message"
                "play_spotify" -> "Non sono riuscito ad aprire Spotify. $message"
                "set_robot_context" -> "Non sono riuscito ad impostare il contesto. $message"
                "web_search" -> "Non sono riuscito a cercare sul web. $message"
                "fetch_url" -> "Non sono riuscito a leggere la pagina. $message"
                "move_body_joint", "move_body_joints" -> "Non sono riuscito a muovere il corpo. $message"
                "body_home" -> "Non sono riuscito a tornare in posizione neutra. $message"
                "body_status" -> "Non sono riuscito a leggere lo stato del corpo. $message"
                "send_whatsapp" -> "Non sono riuscito ad aprire WhatsApp. $message"
                "resolve_whatsapp_target" -> "Non sono riuscito a trovare la chat WhatsApp. $message"
                else -> "Operazione $toolName non riuscita. $message"
            }
        }
    }
    
    private fun logLlmStep(step: Int, parsed: ParsedLlmResponse) {
        val chainLabel = when (val action = parsed.action) {
            is LlmAction.ToolCall -> action.chainStatus.name.lowercase(Locale.ROOT)
            else -> null
        }
        reasoningLogObserver.onLlmStep(
            step = step,
            think = parsed.think,
            reply = parsed.text.takeIf { it.isNotBlank() },
            emotion = parsed.emotion,
            action = parsed.action,
            chainStatusLabel = chainLabel,
        )
    }

    private data class PendingConfirmation(
        val tool: ToolInvocation,
        val lastText: String,
        val lastEmotion: String?,
    )
}
