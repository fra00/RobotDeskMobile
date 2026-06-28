package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.domain.heartbeat.AttentionDomainState
import com.example.mydeskrobot.domain.heartbeat.DomainSensitivity
import com.example.mydeskrobot.domain.heartbeat.DomainTrigger
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.data.presence.DeskPresenceSettings
import com.example.mydeskrobot.data.speech.VoskModelManager
import com.example.mydeskrobot.domain.llm.LlmProvider
import com.example.mydeskrobot.domain.llm.LlmSettings
import com.example.mydeskrobot.domain.speech.SttProvider
import com.example.mydeskrobot.data.body.BodySettings
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
)

fun MemorySettings.toFormState(): MemorySettingsFormState = MemorySettingsFormState(
    enabled = enabled,
    intervalSeconds = intervalSeconds,
)

data class HeartbeatSettingsFormState(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 10,
    val startHour: Int = 7,
    val endHour: Int = 23,
    val proactiveThreshold: Float = 0.75f,
)

fun HeartbeatSettings.toFormState(): HeartbeatSettingsFormState = HeartbeatSettingsFormState(
    enabled = enabled,
    intervalMinutes = intervalMinutes,
    startHour = startHour,
    endHour = endHour,
    proactiveThreshold = proactiveThreshold,
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

enum class AttentionDomainTriggerType {
    DAILY,
    WEEKLY,
    EVENT_PHOTO,
    EVENT_ROOM,
}

enum class AttentionDomainSensitivityOption {
    LOW,
    MEDIUM,
    HIGH,
}

data class AttentionDomainEditorFormState(
    val editingId: String? = null,
    val displayName: String = "",
    val description: String = "",
    val triggerType: AttentionDomainTriggerType = AttentionDomainTriggerType.DAILY,
    val triggerHour: Int = 12,
    val triggerDayOfWeek: Int = java.util.Calendar.MONDAY,
    val sensitivity: AttentionDomainSensitivityOption = AttentionDomainSensitivityOption.MEDIUM,
    val requiresPresenceCheck: Boolean = false,
    val canUseCamera: Boolean = false,
    val enabled: Boolean = true,
)

fun AttentionDomainState.toEditorForm(): AttentionDomainEditorFormState {
    val triggerType = when (val t = trigger) {
        is DomainTrigger.TimeDaily -> AttentionDomainTriggerType.DAILY
        is DomainTrigger.TimeWeekly -> AttentionDomainTriggerType.WEEKLY
        is DomainTrigger.Event -> when (t.eventId) {
            "nuova_foto" -> AttentionDomainTriggerType.EVENT_PHOTO
            "cambio_stanza" -> AttentionDomainTriggerType.EVENT_ROOM
            else -> AttentionDomainTriggerType.EVENT_PHOTO
        }
    }
    val sensitivityOption = when (sensitivity) {
        DomainSensitivity.LOW -> AttentionDomainSensitivityOption.LOW
        DomainSensitivity.MEDIUM -> AttentionDomainSensitivityOption.MEDIUM
        DomainSensitivity.HIGH -> AttentionDomainSensitivityOption.HIGH
    }
    return AttentionDomainEditorFormState(
        editingId = id,
        displayName = displayName,
        description = userPrompt.orEmpty(),
        triggerType = triggerType,
        triggerHour = (trigger as? DomainTrigger.TimeDaily)?.hour ?: 12,
        triggerDayOfWeek = (trigger as? DomainTrigger.TimeWeekly)?.dayOfWeek ?: java.util.Calendar.MONDAY,
        sensitivity = sensitivityOption,
        requiresPresenceCheck = requiresPresenceCheck,
        canUseCamera = canUseCamera,
        enabled = enabled,
    )
}

fun AttentionDomainEditorFormState.toDomainState(
    resolvedId: String,
): AttentionDomainState {
    val trigger = when (triggerType) {
        AttentionDomainTriggerType.DAILY -> DomainTrigger.TimeDaily(hour = triggerHour)
        AttentionDomainTriggerType.WEEKLY -> DomainTrigger.TimeWeekly(dayOfWeek = triggerDayOfWeek)
        AttentionDomainTriggerType.EVENT_PHOTO -> DomainTrigger.Event(eventId = "nuova_foto")
        AttentionDomainTriggerType.EVENT_ROOM -> DomainTrigger.Event(eventId = "cambio_stanza")
    }
    val sensitivityEnum = when (sensitivity) {
        AttentionDomainSensitivityOption.LOW -> DomainSensitivity.LOW
        AttentionDomainSensitivityOption.MEDIUM -> DomainSensitivity.MEDIUM
        AttentionDomainSensitivityOption.HIGH -> DomainSensitivity.HIGH
    }
    return AttentionDomainState(
        id = resolvedId,
        displayName = displayName.trim(),
        enabled = enabled,
        isBuiltIn = false,
        userPrompt = description.trim(),
        trigger = trigger,
        sensitivity = sensitivityEnum,
        lastCheckedAt = null,
        requiresPresenceCheck = requiresPresenceCheck,
        canUseCamera = canUseCamera,
    )
}
