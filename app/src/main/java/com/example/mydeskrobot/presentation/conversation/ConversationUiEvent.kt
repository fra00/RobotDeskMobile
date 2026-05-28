package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.llm.LlmProvider
import com.example.mydeskrobot.presentation.settings.LlmSettingsFormState
import com.example.mydeskrobot.presentation.settings.MemorySettingsFormState

sealed interface ConversationUiEvent {

    data object OnToggleHotwordListening : ConversationUiEvent

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
}
