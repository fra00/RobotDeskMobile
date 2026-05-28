package com.example.mydeskrobot.domain.llm

import kotlinx.coroutines.flow.Flow

interface LlmSettingsRepository {
    val settings: Flow<LlmSettings>

    suspend fun load(): LlmSettings

    suspend fun save(settings: LlmSettings)

    fun isValid(settings: LlmSettings): Boolean

    fun validationError(settings: LlmSettings): String?
}
