package com.example.mydeskrobot.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.interaction.EyePokeSide
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.presentation.conversation.ConversationPhase
import com.example.mydeskrobot.presentation.conversation.ConversationUiEvent
import com.example.mydeskrobot.presentation.conversation.ConversationUiState
import com.example.mydeskrobot.presentation.settings.SettingsUiState
import com.example.mydeskrobot.ui.components.BodySettingsDialog
import com.example.mydeskrobot.ui.components.ConversationHistoryDialog
import com.example.mydeskrobot.ui.components.HeartbeatSettingsDialog
import com.example.mydeskrobot.ui.components.LlmSettingsDialog
import com.example.mydeskrobot.ui.components.MicButton
import com.example.mydeskrobot.ui.components.MemoryExtractingIndicator
import com.example.mydeskrobot.ui.components.ListSettingsDialog
import com.example.mydeskrobot.ui.components.MemorySettingsDialog
import com.example.mydeskrobot.ui.components.PhraseInfoCorner
import com.example.mydeskrobot.ui.components.NotificationSettingsDialog
import com.example.mydeskrobot.ui.components.SettingsDialog
import com.example.mydeskrobot.ui.components.SttSettingsDialog
import com.example.mydeskrobot.ui.components.VoskModelDownloadDialog
import com.example.mydeskrobot.domain.speech.SttProvider
import com.example.mydeskrobot.ui.components.RobotEyes
import com.example.mydeskrobot.ui.components.StandbyStatusIndicator
import com.example.mydeskrobot.ui.components.DrowsyMoodIndicator
import com.example.mydeskrobot.ui.components.HappyMoodIndicator
import com.example.mydeskrobot.ui.components.SleepingZzzIndicator
import com.example.mydeskrobot.ui.components.ThinkingGearIndicator
import com.example.mydeskrobot.ui.theme.MyDeskRobotTheme

@Composable
fun RobotScreen(
    uiState: ConversationUiState,
    settingsUiState: SettingsUiState,
    onEvent: (ConversationUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHistory by remember { mutableStateOf(false) }
    val showHistoryButton = uiState.isHotwordListeningActive || uiState.displayText.isNotBlank()
    val layout = rememberScreenLayout()
    val backgroundTapInteraction = remember { MutableInteractionSource() }
    val canTapBackgroundToWake =
        uiState.isHotwordListeningActive &&
            uiState.phase is ConversationPhase.WaitingForHotword

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = layout.horizontalInset,
                    end = layout.horizontalInset,
                    top = layout.topInset,
                    bottom = layout.bottomInset,
                )
                .then(
                    if (canTapBackgroundToWake) {
                        Modifier.clickable(
                            interactionSource = backgroundTapInteraction,
                            indication = null,
                            onClick = { onEvent(ConversationUiEvent.OnBackgroundTapActivateListening) },
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            RobotEyes(
                emotion = uiState.emotion,
                emotionIntensity = uiState.emotionIntensity,
                modifier = Modifier.fillMaxSize(),
                minEyeSize = layout.minEyeSize,
                maxEyeSize = layout.maxEyeSize,
                eyeGap = layout.eyeGap,
                squishLeft = uiState.eyeSquishLeft,
                squishRight = uiState.eyeSquishRight,
                onLeftEyeClick = { onEvent(ConversationUiEvent.OnEyePoked(EyePokeSide.LEFT)) },
                onRightEyeClick = { onEvent(ConversationUiEvent.OnEyePoked(EyePokeSide.RIGHT)) },
                leftEyeContentDescription = stringResource(R.string.cd_robot_eye_left),
                rightEyeContentDescription = stringResource(R.string.cd_robot_eye_right),
            )

            if (
                uiState.phase is ConversationPhase.Thinking ||
                uiState.phase is ConversationPhase.CapturingImage
            ) {
                ThinkingGearIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = layout.thinkingGearOffsetY)
                        .zIndex(1f),
                    size = layout.thinkingGearSize,
                )
            }

            if (
                uiState.isNightMode &&
                uiState.phase is ConversationPhase.WaitingForHotword &&
                uiState.emotion == RobotEmotion.SLEEPING
            ) {
                SleepingZzzIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = -layout.happyMoodOffsetAboveCenter)
                        .zIndex(1f),
                )
            }

            if (uiState.emotion == RobotEmotion.DROWSY && uiState.phase !is ConversationPhase.Thinking) {
                DrowsyMoodIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = -layout.happyMoodOffsetAboveCenter)
                        .zIndex(1f),
                    iconSize = layout.happyMoodHeartSize,
                )
            }

            if (
                uiState.emotion == RobotEmotion.HAPPY &&
                uiState.phase !is ConversationPhase.Thinking &&
                !uiState.isNightMode
            ) {
                HappyMoodIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = -layout.happyMoodOffsetAboveCenter)
                        .zIndex(1f),
                    heartSize = layout.happyMoodHeartSize,
                )
            }
        }

        StandbyStatusIndicator(
            phase = uiState.phase,
            isHotwordListeningActive = uiState.isHotwordListeningActive,
            emotion = uiState.emotion,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(layout.cornerPadding),
        )

        if (uiState.isMemoryExtracting) {
            MemoryExtractingIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = layout.cornerPadding + if (showHistoryButton) {
                            layout.cornerIconButtonSize + 4.dp
                        } else {
                            0.dp
                        },
                        end = layout.cornerPadding,
                    )
                    .zIndex(1f),
                size = layout.cornerIconButtonSize - 8.dp,
            )
        }

        if (showHistoryButton) {
            IconButton(
                onClick = { showHistory = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(layout.cornerPadding)
                    .size(layout.cornerIconButtonSize),
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = stringResource(R.string.cd_show_conversation_history),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(layout.cornerPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = { onEvent(ConversationUiEvent.OnOpenSettings) },
                modifier = Modifier.size(layout.cornerIconButtonSize),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.cd_open_settings),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            PhraseInfoCorner(
                wakePhraseHint = uiState.wakePhraseHint,
                exitPhraseHint = uiState.exitPhraseHint,
                statusMessage = uiState.statusMessage,
                phase = uiState.phase,
            )
        }

        MicButton(
            phase = uiState.phase,
            isHotwordListeningActive = uiState.isHotwordListeningActive,
            onClick = { onEvent(ConversationUiEvent.OnToggleHotwordListening) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(layout.cornerPadding),
            size = layout.micButtonSize,
        )
    }

    if (showHistory) {
        ConversationHistoryDialog(
            historyText = uiState.displayText,
            onDismiss = { showHistory = false },
        )
    }

    if (settingsUiState.showMainDialog) {
        val sttProviderName = when (settingsUiState.sttProvider) {
            SttProvider.ANDROID -> stringResource(R.string.stt_provider_android)
            SttProvider.VOSK -> stringResource(R.string.stt_provider_vosk)
        }
        SettingsDialog(
            onDismiss = { onEvent(ConversationUiEvent.OnDismissSettings) },
            onOpenLlmSettings = { onEvent(ConversationUiEvent.OnOpenLlmSettings) },
            onOpenMemorySettings = { onEvent(ConversationUiEvent.OnOpenMemorySettings) },
            onOpenListSettings = { onEvent(ConversationUiEvent.OnOpenListSettings) },
            onOpenBodySettings = { onEvent(ConversationUiEvent.OnOpenBodySettings) },
            onOpenSttSettings = { onEvent(ConversationUiEvent.OnOpenSttSettings) },
            onOpenVoskModelSettings = { onEvent(ConversationUiEvent.OnOpenVoskModelSettings) },
            onOpenNotificationSettings = { onEvent(ConversationUiEvent.OnOpenNotificationSettings) },
            onOpenHeartbeatSettings = { onEvent(ConversationUiEvent.OnOpenHeartbeatSettings) },
            isVoskModelReady = settingsUiState.voskModelState is com.example.mydeskrobot.data.speech.VoskModelManager.ModelState.Ready,
            sttProviderName = sttProviderName,
            notificationsEnabled = settingsUiState.notificationsEnabled,
            isNotificationAccessGranted = settingsUiState.notificationAccessGranted,
            heartbeatEnabled = settingsUiState.heartbeatForm.enabled,
            onNotificationsEnabledChange = { onEvent(ConversationUiEvent.OnNotificationEnabledChange(it)) },
            onSaveNotifications = { onEvent(ConversationUiEvent.OnSaveNotificationSettings) },
        )
    }

    if (settingsUiState.showVoskModelDialog) {
        VoskModelDownloadDialog(
            modelState = settingsUiState.voskModelState,
            onDownload = { onEvent(ConversationUiEvent.OnDownloadVoskModel) },
            onDismiss = { onEvent(ConversationUiEvent.OnDismissVoskModelSettings) },
            onSkip = { onEvent(ConversationUiEvent.OnSkipVoskModel) },
        )
    }

    if (settingsUiState.showSttDialog) {
        SttSettingsDialog(
            currentProvider = settingsUiState.sttProvider,
            isVoskModelReady = settingsUiState.voskModelState is com.example.mydeskrobot.data.speech.VoskModelManager.ModelState.Ready,
            onProviderChange = { onEvent(ConversationUiEvent.OnSttProviderChange(it)) },
            onSave = { onEvent(ConversationUiEvent.OnSaveSttSettings) },
            onDismiss = { onEvent(ConversationUiEvent.OnDismissSttSettings) },
        )
    }

    if (settingsUiState.showLlmDialog) {
        LlmSettingsDialog(
            form = settingsUiState.form,
            isSaving = settingsUiState.isSaving,
            isTesting = settingsUiState.isTesting,
            feedbackMessage = settingsUiState.feedbackMessage,
            feedbackIsError = settingsUiState.feedbackIsError,
            onProviderChange = { onEvent(ConversationUiEvent.OnLlmProviderChange(it)) },
            onFormChange = { onEvent(ConversationUiEvent.OnLlmFormChange(it)) },
            onSave = { onEvent(ConversationUiEvent.OnSaveLlmSettings) },
            onTest = { onEvent(ConversationUiEvent.OnTestLlmConnection) },
            onDismiss = { onEvent(ConversationUiEvent.OnDismissLlmSettings) },
        )
    }

    if (settingsUiState.showBodyDialog) {
        BodySettingsDialog(
            form = settingsUiState.bodyForm,
            isTesting = settingsUiState.bodyTesting,
            feedbackMessage = settingsUiState.feedbackMessage,
            feedbackIsError = settingsUiState.feedbackIsError,
            onFormChange = { onEvent(ConversationUiEvent.OnBodyFormChange(it)) },
            onTestConnection = { onEvent(ConversationUiEvent.OnTestBodyConnection) },
            onTestMovement = { onEvent(ConversationUiEvent.OnTestBodyMovement) },
            onSave = { onEvent(ConversationUiEvent.OnSaveBodySettings) },
            onDismiss = { onEvent(ConversationUiEvent.OnDismissBodySettings) },
        )
    }

    if (settingsUiState.showListDialog) {
        ListSettingsDialog(
            listItems = settingsUiState.listEditItems,
            onListItemValueChange = { id, text ->
                onEvent(ConversationUiEvent.OnListItemValueChange(id, text))
            },
            onListItemCheckedChange = { id, checked ->
                onEvent(ConversationUiEvent.OnListItemCheckedChange(id, checked))
            },
            onSaveListItem = { id, text, checked ->
                onEvent(ConversationUiEvent.OnSaveListItem(id, text, checked))
            },
            onDeleteListItem = { id -> onEvent(ConversationUiEvent.OnDeleteListItem(id)) },
            onDismiss = { onEvent(ConversationUiEvent.OnDismissListSettings) },
        )
    }

    if (settingsUiState.showMemoryDialog) {
        MemorySettingsDialog(
            form = settingsUiState.memoryForm,
            memoryItems = settingsUiState.memoryEditItems,
            onFormChange = { onEvent(ConversationUiEvent.OnMemoryFormChange(it)) },
            onMemoryItemValueChange = { id, value ->
                onEvent(ConversationUiEvent.OnMemoryItemValueChange(id, value))
            },
            onSaveMemoryItem = { id, value -> onEvent(ConversationUiEvent.OnSaveMemoryItem(id, value)) },
            onDeleteMemoryItem = { id -> onEvent(ConversationUiEvent.OnDeleteMemoryItem(id)) },
            onSave = { onEvent(ConversationUiEvent.OnSaveMemorySettings) },
            onResetMemory = { onEvent(ConversationUiEvent.OnResetMemoryManual) },
            onReorganizeNow = { onEvent(ConversationUiEvent.OnReorganizeMemoryManual) },
            onDismiss = { onEvent(ConversationUiEvent.OnDismissMemorySettings) },
        )
    }

    if (settingsUiState.showNotificationDialog) {
        NotificationSettingsDialog(
            isEnabled = settingsUiState.notificationsEnabled,
            isNotificationAccessGranted = settingsUiState.notificationAccessGranted,
            allowedPackages = settingsUiState.notificationAllowedPackages,
            onEnabledChange = { onEvent(ConversationUiEvent.OnNotificationEnabledChange(it)) },
            onPackageToggle = { onEvent(ConversationUiEvent.OnNotificationPackageToggle(it)) },
            onDismiss = { onEvent(ConversationUiEvent.OnDismissNotificationSettings) },
            onSave = { onEvent(ConversationUiEvent.OnSaveNotificationSettings) },
        )
    }

    if (settingsUiState.showHeartbeatDialog) {
        HeartbeatSettingsDialog(
            form = settingsUiState.heartbeatForm,
            onFormChange = { onEvent(ConversationUiEvent.OnHeartbeatFormChange(it)) },
            onSave = { onEvent(ConversationUiEvent.OnSaveHeartbeatSettings) },
            onDismiss = { onEvent(ConversationUiEvent.OnDismissHeartbeatSettings) },
        )
    }
}

private data class ScreenLayout(
    val cornerPadding: Dp,
    val horizontalInset: Dp,
    val topInset: Dp,
    val bottomInset: Dp,
    val minEyeSize: Dp,
    val maxEyeSize: Dp,
    val eyeGap: Dp,
    val micButtonSize: Dp,
    val cornerIconButtonSize: Dp,
    val thinkingGearSize: Dp,
    val thinkingGearOffsetY: Dp,
    val happyMoodHeartSize: Dp,
    /** Spostamento verso l'alto dal centro (sopra gli occhi). */
    val happyMoodOffsetAboveCenter: Dp,
)

@Composable
private fun rememberScreenLayout(): ScreenLayout {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    return if (isLandscape) {
        ScreenLayout(
            cornerPadding = 12.dp,
            horizontalInset = 88.dp,
            topInset = 52.dp,
            bottomInset = 52.dp,
            minEyeSize = 72.dp,
            maxEyeSize = 200.dp,
            eyeGap = 40.dp,
            micButtonSize = 56.dp,
            cornerIconButtonSize = 44.dp,
            thinkingGearSize = 40.dp,
            thinkingGearOffsetY = 24.dp,
            happyMoodHeartSize = 32.dp,
            happyMoodOffsetAboveCenter = 88.dp,
        )
    } else {
        ScreenLayout(
            cornerPadding = 16.dp,
            horizontalInset = 16.dp,
            topInset = 56.dp,
            bottomInset = 88.dp,
            minEyeSize = 72.dp,
            maxEyeSize = 160.dp,
            eyeGap = 32.dp,
            micButtonSize = 64.dp,
            cornerIconButtonSize = 48.dp,
            thinkingGearSize = 48.dp,
            thinkingGearOffsetY = 32.dp,
            happyMoodHeartSize = 36.dp,
            happyMoodOffsetAboveCenter = 72.dp,
        )
    }
}

@Preview(showBackground = true, name = "Portrait")
@Composable
private fun RobotScreenPortraitPreview() {
    MyDeskRobotTheme {
        BoxWithConstraints {
            RobotScreen(
                uiState = previewActiveState(),
                settingsUiState = SettingsUiState(),
                onEvent = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(
    showBackground = true,
    name = "Landscape",
    widthDp = 840,
    heightDp = 400,
)
@Composable
private fun RobotScreenLandscapePreview() {
    MyDeskRobotTheme {
        RobotScreen(
            uiState = previewActiveState(),
            settingsUiState = SettingsUiState(),
            onEvent = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, name = "Sleeping")
@Composable
private fun RobotScreenSleepingPreview() {
    MyDeskRobotTheme {
        RobotScreen(
            uiState = ConversationUiState(
                phase = ConversationPhase.WaitingForHotword,
                emotion = RobotEmotion.SLEEPING,
                statusMessage = "Stanotte dormo…",
                wakePhraseHint = "Attivazione: «assistente»",
                exitPhraseHint = "Uscita: «adesso basta»",
                isHotwordListeningActive = true,
                isNightMode = true,
            ),
            settingsUiState = SettingsUiState(),
            onEvent = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, name = "Happy")
@Composable
private fun RobotScreenHappyPreview() {
    MyDeskRobotTheme {
        RobotScreen(
            uiState = ConversationUiState(
                phase = ConversationPhase.ActiveListening,
                emotion = RobotEmotion.HAPPY,
                statusMessage = "Felice!",
                wakePhraseHint = "Attivazione: «assistente»",
                exitPhraseHint = "Uscita: «adesso basta»",
                isHotwordListeningActive = true,
            ),
            settingsUiState = SettingsUiState(),
            onEvent = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun previewActiveState() = ConversationUiState(
    phase = ConversationPhase.ActiveListening,
    emotion = RobotEmotion.LISTENING,
    statusMessage = "In ascolto…",
    conversationLog = "Tu: ciao\n\nRobot: Ciao!",
    currentUtterance = "come stai",
    wakePhraseHint = "Attivazione: «assistente»",
    exitPhraseHint = "Uscita: «adesso basta» · pausa 5 s",
    isHotwordListeningActive = true,
)
