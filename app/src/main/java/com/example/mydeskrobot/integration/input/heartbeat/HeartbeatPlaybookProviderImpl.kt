package com.example.mydeskrobot.integration.input.heartbeat

import android.content.Context
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.domain.wellness.WellnessPhase
import com.example.mydeskrobot.reasoning.HeartbeatPlaybookProvider
import com.example.mydeskrobot.reasoning.model.RobotInput

class HeartbeatPlaybookProviderImpl(
    private val context: Context,
) : HeartbeatPlaybookProvider {

    private val promptText: String by lazy { LlmPromptLoader.loadHeartbeatPlaybookPrompt(context) }

    override suspend fun buildContextSection(input: RobotInput?): String {
        return when (input) {
            is RobotInput.Heartbeat -> buildString {
                append(promptText)
                val domainId = input.activeDomainId
                if (!domainId.isNullOrBlank()) {
                    append("\n\n")
                    append("=== DOMINIO: ${input.activeDomainName ?: domainId} ===\n")
                    append(loadDomainPrompt(domainId, input.activeDomainUserPrompt))
                }
            }
            is RobotInput.WeeklyReflection -> promptText
            is RobotInput.PredictivityDeviation -> loadPredictivityDeviationPrompt()
            is RobotInput.WellnessCheck -> when (input.phase) {
                WellnessPhase.VISUAL_ORDER -> loadRoomOrderAuditPrompt()
                WellnessPhase.DOMAIN_SCORE -> loadWellnessCheckPrompt()
            }
            else -> ""
        }
    }

    private fun loadWellnessCheckPrompt(): String =
        LlmPromptLoader.loadWellnessCheckPrompt(context)

    private fun loadRoomOrderAuditPrompt(): String =
        LlmPromptLoader.loadRoomOrderAuditPrompt(context)

    private fun loadPredictivityDeviationPrompt(): String =
        LlmPromptLoader.loadPredictivityDeviationPrompt(context)

    private fun loadDomainPrompt(domainId: String, userPrompt: String?): String {
        if (!userPrompt.isNullOrBlank()) return userPrompt
        val path = "prompts/domains/$domainId.txt"
        return runCatching { LlmPromptLoader.loadOptionalAsset(context, path) }
            .getOrDefault("")
    }
}
