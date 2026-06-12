package com.example.mydeskrobot.integration.body

import android.content.Context
import com.example.mydeskrobot.data.body.BodySettingsRepository
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.reasoning.BodyCapabilitiesProvider

class BodyPromptProviderImpl(
    private val context: Context,
    private val settingsRepository: BodySettingsRepository,
) : BodyCapabilitiesProvider {

    private val promptText: String by lazy { LlmPromptLoader.loadBodyCapabilitiesPrompt(context) }

    override suspend fun buildContextSection(): String {
        val settings = settingsRepository.load()
        if (!settings.isConfigured()) return ""
        return promptText
    }
}
