package com.example.mydeskrobot.integration.input.heartbeat

import android.content.Context
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.reasoning.HeartbeatPlaybookProvider
import com.example.mydeskrobot.reasoning.model.RobotInput

class HeartbeatPlaybookProviderImpl(
    private val context: Context,
) : HeartbeatPlaybookProvider {

    private val promptText: String by lazy { LlmPromptLoader.loadHeartbeatPlaybookPrompt(context) }

    override suspend fun buildContextSection(input: RobotInput?): String {
        return when (input) {
            is RobotInput.Heartbeat, is RobotInput.WeeklyReflection -> promptText
            else -> ""
        }
    }
}
