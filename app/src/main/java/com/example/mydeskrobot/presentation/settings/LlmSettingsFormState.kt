package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.domain.proactive.ProactivityConstants
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.data.presence.DeskPresenceSettings
import com.example.mydeskrobot.data.speech.VoskModelManager
import com.example.mydeskrobot.domain.llm.LlmProvider
import com.example.mydeskrobot.domain.llm.LlmSettings
import com.example.mydeskrobot.domain.speech.SttProvider
import com.example.mydeskrobot.data.body.BodySettings
import com.example.mydeskrobot.memory.MemoryReorganizePolicy
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
    val showLogDayDialog: Boolean = false,
    val showListDialog: Boolean = false,
    val showBodyDialog: Boolean = false,
    val showVoskModelDialog: Boolean = false,
    val showSttDialog: Boolean = false,
    val showNotificationDialog: Boolean = false,
    val showHeartbeatDialog: Boolean = false,
    val showDeskPresenceDialog: Boolean = false,
    val showAttentionDomainsDialog: Boolean = false,
    val showAttentionDomainEditor: Boolean = false,
    val attentionDomainEditorForm: AttentionDomainEditorFormState = AttentionDomainEditorFormState(),
    val attentionDomainEditorError: String? = null,
    val attentionDomainDeleteConfirmId: String? = null,
    val attentionDomainsReturnToMain: Boolean = false,
    val attentionDomainsSummary: String = "",
    val showSpatialDialog: Boolean = false,
    val form: LlmSettingsFormState = LlmSettingsFormState(),
    val memoryForm: MemorySettingsFormState = MemorySettingsFormState(),
    val logDayForm: LogDaySettingsFormState = LogDaySettingsFormState(),
    val logDayGroups: List<DayActivityGroupUi> = emptyList(),
    val heartbeatForm: HeartbeatSettingsFormState = HeartbeatSettingsFormState(),
    val deskPresenceForm: DeskPresenceSettingsFormState = DeskPresenceSettingsFormState(),
    val attentionDomains: List<AttentionDomainUiState> = emptyList(),
    val voskModelState: VoskModelManager.ModelState = VoskModelManager.ModelState.NotDownloaded,
    val sttProvider: SttProvider = SttProvider.ANDROID,
    val notificationsEnabled: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val notificationAllowedPackages: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val memoryEditItems: List<MemoryItemUi> = emptyList(),
    val listEditItems: List<ListItemUi> = emptyList(),
    val spatialEditItems: List<SpatialPlaceUi> = emptyList(),
    val bodyForm: BodySettingsFormState = BodySettingsFormState(),
    val bodyTesting: Boolean = false,
    val memoryReorganizing: Boolean = false,
    val memoryReorganizeHint: String? = null,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
)

data class BodySettingsFormState(
    val enabled: Boolean = false,
    val baseUrl: String = "",
)

fun BodySettings.toFormState(): BodySettingsFormState = BodySettingsFormState(
    enabled = enabled,
    baseUrl = baseUrl,
)

data class MemorySettingsFormState(
    val enabled: Boolean = true,
    val intervalSeconds: Long = 45L,
    val autoReorganizeEnabled: Boolean = true,
    val reorganizeMinRows: Int = MemoryReorganizePolicy.DEFAULT_MIN_USER_FACING_ROWS,
    val reorganizeCooldownDays: Long = MemoryReorganizePolicy.DEFAULT_COOLDOWN_DAYS,
)

fun MemorySettings.toFormState(): MemorySettingsFormState = MemorySettingsFormState(
    enabled = enabled,
    intervalSeconds = intervalSeconds,
    autoReorganizeEnabled = autoReorganizeEnabled,
    reorganizeMinRows = reorganizeMinRows,
    reorganizeCooldownDays = reorganizeCooldownDays,
)

data class HeartbeatSettingsFormState(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 10,
    val startHour: Int = 7,
    val endHour: Int = 23,
    val proactiveThreshold: Float = 0.75f,
    val predictivityEnabled: Boolean = true,
    val wellnessEnabled: Boolean = true,
    val wellnessAnchorMinutes: Int = 60,
    val wellnessIdleMinutes: Int = ProactivityConstants.WELLNESS_IDLE_MINUTES,
    val wellnessPresenceMinutes: Int = 15,
)

fun HeartbeatSettings.toFormState(
    proactivity: com.example.mydeskrobot.data.proactive.ProactivitySettings =
        com.example.mydeskrobot.data.proactive.ProactivitySettings(),
): HeartbeatSettingsFormState = HeartbeatSettingsFormState(
    enabled = enabled,
    intervalMinutes = intervalMinutes,
    startHour = startHour,
    endHour = endHour,
    proactiveThreshold = proactiveThreshold,
    predictivityEnabled = proactivity.predictivityEnabled,
    wellnessEnabled = proactivity.wellnessEnabled,
    wellnessAnchorMinutes = proactivity.wellnessAnchorMinutes,
    wellnessIdleMinutes = proactivity.wellnessIdleMinutes,
    wellnessPresenceMinutes = proactivity.wellnessPresenceMinutes,
)

data class DeskPresenceSettingsFormState(
    val enabled: Boolean = true,
    val analysisFps: Int = 5,
    val faceConfidenceThreshold: Float = 0.6f,
)

fun DeskPresenceSettings.toFormState(): DeskPresenceSettingsFormState = DeskPresenceSettingsFormState(
    enabled = enabled,
    analysisFps = analysisFps,
    faceConfidenceThreshold = faceConfidenceThreshold,
)

data class AttentionDomainUiState(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val subtitle: String,
    val isBuiltIn: Boolean = true,
)
