package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.interaction.EyePokeSide
import com.example.mydeskrobot.domain.llm.LlmProvider
import com.example.mydeskrobot.domain.mood.IdleDistractionKind
import com.example.mydeskrobot.presentation.settings.BodySettingsFormState
import com.example.mydeskrobot.presentation.settings.DeskPresenceSettingsFormState
import com.example.mydeskrobot.presentation.settings.HeartbeatSettingsFormState
import com.example.mydeskrobot.presentation.settings.LlmSettingsFormState
import com.example.mydeskrobot.presentation.settings.LogDaySettingsFormState
import com.example.mydeskrobot.presentation.settings.MemorySettingsFormState

sealed interface ConversationUiEvent {

    data object OnToggleHotwordListening : ConversationUiEvent

    /** Tap on background (not controls) — same as wake word when in standby. */
    data object OnBackgroundTapActivateListening : ConversationUiEvent

    data class OnEyePoked(val side: EyePokeSide) : ConversationUiEvent

    data class OnCancelPendingInboxItem(val id: String) : ConversationUiEvent

    data object OnOpenSettings : ConversationUiEvent

    data object OnDismissSettings : ConversationUiEvent

    data object OnOpenLlmSettings : ConversationUiEvent

    data object OnDismissLlmSettings : ConversationUiEvent

    data object OnOpenMemorySettings : ConversationUiEvent

    data object OnDismissMemorySettings : ConversationUiEvent

    data object OnOpenSpatialSettings : ConversationUiEvent

    data object OnDismissSpatialSettings : ConversationUiEvent

    data class OnSpatialPlaceLabelChange(val id: Long, val label: String) : ConversationUiEvent

    data class OnSpatialPlaceLandmarksChange(val id: Long, val landmarks: String) : ConversationUiEvent

    data class OnSaveSpatialPlace(val id: Long, val label: String, val landmarks: String) : ConversationUiEvent

    data class OnDeleteSpatialPlace(val id: Long) : ConversationUiEvent

    data object OnOpenLogDaySettings : ConversationUiEvent

    data object OnDismissLogDaySettings : ConversationUiEvent

    data class OnLogDayFormChange(val form: LogDaySettingsFormState) : ConversationUiEvent

    data object OnSaveLogDaySettings : ConversationUiEvent

    data object OnRefreshHabitSummary : ConversationUiEvent

    data object OnClearActivityLog : ConversationUiEvent

    data class OnLlmProviderChange(val provider: LlmProvider) : ConversationUiEvent

    data class OnLlmFormChange(val form: LlmSettingsFormState) : ConversationUiEvent

    data object OnSaveLlmSettings : ConversationUiEvent

    data object OnTestLlmConnection : ConversationUiEvent

    data class OnMemoryFormChange(val form: MemorySettingsFormState) : ConversationUiEvent

    data object OnSaveMemorySettings : ConversationUiEvent

    data object OnResetMemoryManual : ConversationUiEvent

    data object OnReorganizeMemoryManual : ConversationUiEvent

    data class OnMemoryItemValueChange(val id: Long, val value: String) : ConversationUiEvent

    data class OnSaveMemoryItem(val id: Long, val value: String) : ConversationUiEvent

    data class OnDeleteMemoryItem(val id: Long) : ConversationUiEvent

    data object OnOpenListSettings : ConversationUiEvent

    data object OnDismissListSettings : ConversationUiEvent

    data class OnListItemValueChange(val id: Long, val text: String) : ConversationUiEvent

    data class OnListItemCheckedChange(val id: Long, val checked: Boolean) : ConversationUiEvent

    data class OnSaveListItem(val id: Long, val text: String, val checked: Boolean) : ConversationUiEvent

    data class OnDeleteListItem(val id: Long) : ConversationUiEvent

    data object OnOpenBodySettings : ConversationUiEvent

    data object OnDismissBodySettings : ConversationUiEvent

    data class OnBodyFormChange(val form: BodySettingsFormState) : ConversationUiEvent

    data object OnSaveBodySettings : ConversationUiEvent

    data object OnTestBodyConnection : ConversationUiEvent

    data object OnTestBodyMovement : ConversationUiEvent

    data object OnOpenVoskModelSettings : ConversationUiEvent

    data object OnDismissVoskModelSettings : ConversationUiEvent

    data object OnDownloadVoskModel : ConversationUiEvent

    data object OnSkipVoskModel : ConversationUiEvent

    data object OnOpenSttSettings : ConversationUiEvent

    data object OnDismissSttSettings : ConversationUiEvent

    data class OnSttProviderChange(val provider: com.example.mydeskrobot.domain.speech.SttProvider) : ConversationUiEvent

    data object OnSaveSttSettings : ConversationUiEvent

    data object OnOpenNotificationSettings : ConversationUiEvent

    data object OnDismissNotificationSettings : ConversationUiEvent

    data class OnNotificationEnabledChange(val enabled: Boolean) : ConversationUiEvent

    data class OnNotificationPackageToggle(val packageName: String) : ConversationUiEvent

    data object OnSaveNotificationSettings : ConversationUiEvent

    data object OnOpenHeartbeatSettings : ConversationUiEvent

    data object OnDismissHeartbeatSettings : ConversationUiEvent

    data class OnHeartbeatFormChange(val form: HeartbeatSettingsFormState) : ConversationUiEvent

    data object OnSaveHeartbeatSettings : ConversationUiEvent

    data object OnOpenDeskPresenceSettings : ConversationUiEvent

    data object OnDismissDeskPresenceSettings : ConversationUiEvent

    data class OnDeskPresenceFormChange(val form: DeskPresenceSettingsFormState) : ConversationUiEvent

    data object OnSaveDeskPresenceSettings : ConversationUiEvent

    data class OnOpenAttentionDomainsSettings(
        val returnToMain: Boolean = false,
    ) : ConversationUiEvent

    data object OnDismissAttentionDomainsSettings : ConversationUiEvent

    data class OnToggleAttentionDomain(val domainId: String, val enabled: Boolean) : ConversationUiEvent

    data object OnSaveAttentionDomainsSettings : ConversationUiEvent

    data object OnAddAttentionDomain : ConversationUiEvent

    data class OnEditAttentionDomain(val domainId: String) : ConversationUiEvent

    data class OnDeleteAttentionDomain(val domainId: String) : ConversationUiEvent

    data object OnConfirmDeleteAttentionDomain : ConversationUiEvent

    data object OnDismissDeleteAttentionDomain : ConversationUiEvent

    data class OnAttentionDomainEditorFormChange(
        val form: com.example.mydeskrobot.presentation.settings.AttentionDomainEditorFormState,
    ) : ConversationUiEvent

    data object OnSaveAttentionDomainEditor : ConversationUiEvent

    data object OnDismissAttentionDomainEditor : ConversationUiEvent

    data object OnClearReasoningLog : ConversationUiEvent

    /** Debug: force a symbolic idle distraction overlay (mood dialog). */
    data class OnDebugIdleDistraction(val kind: IdleDistractionKind) : ConversationUiEvent

    /** Debug: clear active idle distraction / relief. */
    data object OnDebugClearIdleDistraction : ConversationUiEvent
}
