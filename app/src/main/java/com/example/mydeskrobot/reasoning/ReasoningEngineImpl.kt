package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.memory.FreshVisionVerifyPrompt
import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlan
import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlanner
import com.example.mydeskrobot.reasoning.memory.MemoryRetrievalProfile
import com.example.mydeskrobot.reasoning.memory.RecallPlanException
import com.example.mydeskrobot.reasoning.memory.RecallPlanFailure
import com.example.mydeskrobot.reasoning.memory.userMessage
import com.example.mydeskrobot.reasoning.model.IntermediateResponse
import com.example.mydeskrobot.reasoning.model.ReasoningResult
import com.example.mydeskrobot.reasoning.model.RobotInput
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
    private val memoryRecallPlanner: MemoryRecallPlanner? = null,
    private val memoryContextProvider: MemoryContextProvider? = null,
    private val spatialContextProvider: SpatialContextProvider? = null,
    private val bodyCapabilitiesProvider: BodyCapabilitiesProvider? = null,
    private val robotContextProvider: RobotContextProvider? = null,
    private val moodContextProvider: MoodContextProvider? = null,
    private val heartbeatPlaybookProvider: HeartbeatPlaybookProvider? = null,
    maxChainSteps: Int = 10,
    private val reasoningLogObserver: ReasoningLogObserver = NoOpReasoningLogObserver,
) : ReasoningEngine {

    companion object {
        private const val DATETIME_PLACEHOLDER = "{{CURRENT_DATETIME}}"
    }
    
    private val responseParser = LlmResponseParser()

    private val orchestrator: ToolChainOrchestrator

    init {
        orchestrator = ToolChainOrchestrator(
            llmClient = llmClient,
            toolExecutor = toolExecutor,
            responseParser = responseParser,
            systemPrompt = buildFullSystemPrompt(),
            maxChainSteps = maxChainSteps,
            onBeforeLlmTurn = { hasPendingImage, _ ->
                if (hasPendingImage && memoryContextProvider != null) {
                    refreshSystemPromptForVision(orchestrator.getOriginalUserText())
                }
            },
            reasoningLogObserver = reasoningLogObserver,
        )
    }
    
    override suspend fun processUserInput(
        userText: String,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        if (userText.isBlank()) {
            return ReasoningResult.Error("Empty input")
        }
        refreshSystemPrompt(userText).onFailure { failure ->
            return ReasoningResult.Error(recallPlanErrorMessage(failure))
        }
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
        refreshSystemPrompt(userText).onFailure { failure ->
            return ReasoningResult.Error(recallPlanErrorMessage(failure))
        }
        return orchestrator.processUserInputWithImage(userText, imageBytes, onIntermediateResponse)
    }
    
    override suspend fun continueAfterConfirmation(
        confirmed: Boolean,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        return orchestrator.continueAfterConfirmation(confirmed, onIntermediateResponse)
    }

    override fun cancelPendingConfirmation() {
        orchestrator.cancelPendingConfirmation()
    }

    override suspend fun processSystemInput(
        envelope: SystemInputEnvelope,
        onIntermediateResponse: suspend (IntermediateResponse) -> Unit,
    ): ReasoningResult {
        if (envelope.formattedForLlm.isBlank()) {
            return ReasoningResult.Error("Empty system input")
        }
        refreshSystemPromptForSystemInput(envelope.input)
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

    private suspend fun refreshSystemPrompt(userText: String): Result<Unit> {
        return buildPromptWithContext(userText).map { prompt ->
            orchestrator.updateSystemPrompt(prompt)
        }
    }

    private suspend fun refreshSystemPromptForSystemInput(systemInput: RobotInput) {
        buildPromptWithContext(userText = "", systemInput = systemInput)
            .onSuccess { orchestrator.updateSystemPrompt(it) }
    }

    private suspend fun refreshSystemPromptForVision(userText: String) {
        buildPromptWithContext(
            userText = userText,
            memoryProfileOverride = MemoryRetrievalProfile.VISION,
            memoryOptions = MemoryContextOptions(freshVisionVerify = true),
        ).onSuccess { orchestrator.updateSystemPrompt(it) }
    }

    private suspend fun buildPromptWithContext(
        userText: String,
        memoryProfileOverride: MemoryRetrievalProfile? = null,
        systemInput: RobotInput? = null,
        memoryOptions: MemoryContextOptions = MemoryContextOptions(),
    ): Result<String> {
        val recallPlanResult = resolveRecallPlan(
            userText = userText,
            memoryProfileOverride = memoryProfileOverride,
            systemInput = systemInput,
            memoryOptions = memoryOptions,
        )
        if (recallPlanResult.isFailure) {
            return Result.failure(recallPlanResult.exceptionOrNull()!!)
        }
        val recallPlan = recallPlanResult.getOrNull()

        val toolPrompt = buildFullSystemPrompt()
        val bodyContext = bodyCapabilitiesProvider?.buildContextSection().orEmpty()
        val heartbeatContext = heartbeatPlaybookProvider
            ?.buildContextSection(systemInput)
            .orEmpty()
        val spatialContext = when {
            memoryOptions.freshVisionVerify -> FreshVisionVerifyPrompt.SECTION
            recallPlan?.localizeSpatial == true -> spatialContextProvider?.buildContextSection(
                SpatialContextOptions(identityOnly = true),
            ).orEmpty()
            else -> ""
        }
        val memoryContext = memoryContextProvider
            ?.buildContextFor(
                userText = userText,
                recallPlan = recallPlan,
                profileOverride = memoryProfileOverride,
                options = memoryOptions,
            )
            .orEmpty()
        val robotContext = robotContextProvider?.buildContextSection().orEmpty()
        val moodContext = moodContextProvider?.buildContextSection().orEmpty()

        return Result.success(
            buildString {
                append(toolPrompt)
                if (bodyContext.isNotBlank()) {
                    append("\n\n")
                    append(bodyContext)
                }
                if (heartbeatContext.isNotBlank()) {
                    append("\n\n")
                    append(heartbeatContext)
                }
                if (spatialContext.isNotBlank()) {
                    append("\n\n")
                    append(spatialContext)
                }
                if (memoryContext.isNotBlank()) {
                    append("\n\n")
                    append(memoryContext)
                }
                if (robotContext.isNotBlank()) {
                    append("\n\n")
                    append(robotContext)
                }
                if (moodContext.isNotBlank()) {
                    append("\n\n")
                    append(moodContext)
                }
            },
        )
    }

    private suspend fun resolveRecallPlan(
        userText: String,
        memoryProfileOverride: MemoryRetrievalProfile?,
        systemInput: RobotInput?,
        memoryOptions: MemoryContextOptions,
    ): Result<MemoryRecallPlan?> {
        if (memoryProfileOverride == MemoryRetrievalProfile.VISION || memoryOptions.freshVisionVerify) {
            return Result.success(MemoryRecallPlan.visionCatalog())
        }
        if (systemInput != null || userText.isBlank()) {
            return Result.success(null)
        }
        if (memoryContextProvider == null) {
            return Result.success(null)
        }
        val planner = memoryRecallPlanner
            ?: return Result.failure(RecallPlanException(RecallPlanFailure.NotConfigured))
        return planner.plan(userText, System.currentTimeMillis()).fold(
            onSuccess = { plan -> Result.success(if (plan.skipRecall) null else plan) },
            onFailure = { Result.failure(it) },
        )
    }

    private fun recallPlanErrorMessage(failure: Throwable): String {
        val planFailure = (failure as? RecallPlanException)?.failure
        return planFailure?.userMessage() ?: failure.message ?: "Errore nel preparare il contesto memoria."
    }
}
