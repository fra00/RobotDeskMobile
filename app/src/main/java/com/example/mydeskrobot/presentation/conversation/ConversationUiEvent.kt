package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.interaction.EyePokeSide
import com.example.mydeskrobot.domain.llm.LlmProvider
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
