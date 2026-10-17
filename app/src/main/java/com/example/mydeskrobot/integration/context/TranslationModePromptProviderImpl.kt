package com.example.mydeskrobot.integration.context

import com.example.mydeskrobot.data.translation.TranslationModeRepository
import com.example.mydeskrobot.domain.translation.TranslationModePolicy
import com.example.mydeskrobot.reasoning.TranslationModeProvider

class TranslationModePromptProviderImpl(
    private val repository: TranslationModeRepository,
) : TranslationModeProvider {
    override suspend fun buildContextSection(): String {
        return TranslationModePolicy.buildPromptSection(repository.getState())
    }
}
