package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.data.speech.VoskModelManager
import com.example.mydeskrobot.domain.llm.LlmProvider
import com.example.mydeskrobot.domain.llm.LlmSettings
import com.example.mydeskrobot.domain.speech.SttProvider
import com.example.mydeskrobot.memory.MemorySettings

data class LlmSettingsFormState(
    val provider: LlmProvider = LlmProvider.LM_STUDIO,
    val baseUrl: String = "",
    val textModel: String = "",
    val visionModel: String = "",
    val apiKey: String = "",
)

fun LlmSettings.toFormState(): LlmSettingsFormState = LlmSettingsFormState(
    provider = provider,
    baseUrl = baseUrl,
    textModel = textModel,
    visionModel = visionModel,
    apiKey = apiKey,
)

fun LlmSettingsFormState.toDomain(): LlmSettings = LlmSettings(
    provider = provider,
    baseUrl = baseUrl.trim(),
    textModel = textModel.trim(),
    visionModel = visionModel.trim(),
    apiKey = apiKey,
)

data class SettingsUiState(
    val showMainDialog: Boolean = false,
    val showLlmDialog: Boolean = false,
    val showMemoryDialog: Boolean = false,
    val showVoskModelDialog: Boolean = false,
    val showSttDialog: Boolean = false,
    val showNotificationDialog: Boolean = false,
    val form: LlmSettingsFormState = LlmSettingsFormState(),
    val memoryForm: MemorySettingsFormState = MemorySettingsFormState(),
    val voskModelState: VoskModelManager.ModelState = VoskModelManager.ModelState.NotDownloaded,
    val sttProvider: SttProvider = SttProvider.ANDROID,
    val notificationsEnabled: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val notificationAllowedPackages: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val memoryListPreview: List<String> = emptyList(),
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
)

data class MemorySettingsFormState(
    val enabled: Boolean = true,
    val intervalSeconds: Long = 45L,
)

fun MemorySettings.toFormState(): MemorySettingsFormState = MemorySettingsFormState(
    enabled = enabled,
    intervalSeconds = intervalSeconds,
)
