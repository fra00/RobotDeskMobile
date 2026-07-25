package com.example.mydeskrobot.integration.input.heartbeat

import android.content.Context
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.domain.wellness.WellnessPhase
import com.example.mydeskrobot.reasoning.HeartbeatPlaybookProvider
import com.example.mydeskrobot.reasoning.model.RobotInput

/**
 * Injects situational prompt sections for system inputs (reflection, wellness, predictivity).
 * Name retained for wiring stability; no longer serves LLM heartbeat domain ticks.
 */
class HeartbeatPlaybookProviderImpl(
    private val context: Context,
) : HeartbeatPlaybookProvider {

    private val promptText: String by lazy { LlmPromptLoader.loadHeartbeatPlaybookPrompt(context) }

    override suspend fun buildContextSection(input: RobotInput?): String {
        return when (input) {
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
}
