package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.interaction.EyePokeSide
import com.example.mydeskrobot.domain.llm.LlmProvider
import com.example.mydeskrobot.presentation.settings.BodySettingsFormState
import com.example.mydeskrobot.presentation.settings.HeartbeatSettingsFormState
import com.example.mydeskrobot.presentation.settings.LlmSettingsFormState
import com.example.mydeskrobot.presentation.settings.MemorySettingsFormState

sealed interface ConversationUiEvent {

    data object OnToggleHotwordListening : ConversationUiEvent

    /** Tap on background (not controls) — same as wake word when in standby. */
    data object OnBackgroundTapActivateListening : ConversationUiEvent

    data class OnEyePoked(val side: EyePokeSide) : ConversationUiEvent

    data object OnOpenSettings : ConversationUiEvent

    data object OnDismissSettings : ConversationUiEvent

    data object OnOpenLlmSettings : ConversationUiEvent

    data object OnDismissLlmSettings : ConversationUiEvent

    data object OnOpenMemorySettings : ConversationUiEvent

    data object OnDismissMemorySettings : ConversationUiEvent

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
}
