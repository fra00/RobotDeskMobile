package com.example.mydeskrobot.presentation.conversation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mydeskrobot.data.hotword.HotwordController
import com.example.mydeskrobot.data.hotword.HotwordServiceStarter
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.domain.hotword.HotwordEvent
import com.example.mydeskrobot.domain.hotword.HotwordEventDispatcher
import com.example.mydeskrobot.domain.hotword.SessionEndReason
import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent
import com.example.mydeskrobot.domain.memory.ConversationTopicExtractor
import com.example.mydeskrobot.domain.model.BoredIdleConfig
import com.example.mydeskrobot.domain.model.LlmAssistantReply
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.time.NightModeConfig
import com.example.mydeskrobot.domain.time.NightModeHelper
import kotlinx.coroutines.isActive
import com.example.mydeskrobot.data.llm.LlmRepositoryImpl
import com.example.mydeskrobot.domain.repository.LlmRepository
import com.example.mydeskrobot.domain.repository.SpeechToTextRepository
import com.example.mydeskrobot.domain.repository.TextToSpeechRepository
import com.example.mydeskrobot.domain.vision.VisionImageCapture
import com.example.mydeskrobot.data.speech.TtsInterruptedException
import com.example.mydeskrobot.domain.speech.EchoSpeechFilter
import com.example.mydeskrobot.domain.speech.MarkdownStripper
import com.example.mydeskrobot.domain.speech.VoiceConfirmationDecision
import com.example.mydeskrobot.domain.speech.VoiceConfirmationMatcher
import com.example.mydeskrobot.domain.llm.LlmEmotionMapper
import com.example.mydeskrobot.domain.llm.LlmSettings
import com.example.mydeskrobot.domain.llm.LlmSettingsRepository
import com.example.mydeskrobot.integration.ReasoningModule
import com.example.mydeskrobot.integration.llm.LlmClientFactory
import com.example.mydeskrobot.integration.llm.LlmHttpErrors
import com.example.mydeskrobot.memory.ForgetByTopicResult
import com.example.mydeskrobot.memory.MemorySettingsRepository
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.activity.extract.ActivityExtractionService
import com.example.mydeskrobot.activity.extract.ActivityLogExtractionScheduler
import com.example.mydeskrobot.activity.summary.ActivityHabitSummarizer
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.activitylog.ActivityLogSettingsRepository
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.memory.extract.MemoryExtractionScheduler
import com.example.mydeskrobot.memory.extract.MemoryExtractionService
import com.example.mydeskrobot.presentation.settings.BodySettingsFormState
import com.example.mydeskrobot.presentation.settings.HeartbeatSettingsFormState
import com.example.mydeskrobot.presentation.settings.LlmSettingsFormState
import com.example.mydeskrobot.presentation.settings.ListItemUi
import com.example.mydeskrobot.presentation.settings.MemoryItemUi
import com.example.mydeskrobot.presentation.settings.ActivityLogItemUi
import com.example.mydeskrobot.presentation.settings.DayActivityGroupUi
import com.example.mydeskrobot.presentation.settings.LogDaySettingsFormState
import com.example.mydeskrobot.presentation.settings.MemorySettingsFormState
import com.example.mydeskrobot.presentation.settings.toLogDayFormState
import com.example.mydeskrobot.presentation.settings.toListItemUi
import com.example.mydeskrobot.presentation.settings.toUi
import com.example.mydeskrobot.presentation.settings.SpatialPlaceUi
import com.example.mydeskrobot.presentation.settings.SettingsUiState
import com.example.mydeskrobot.presentation.settings.toDomain
import com.example.mydeskrobot.presentation.settings.toFormState
import com.example.mydeskrobot.reasoning.ReasoningEngine
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import com.example.mydeskrobot.reasoning.model.IntermediateResponse
import com.example.mydeskrobot.reasoning.model.ReasoningResult
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.RobotProfile
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope
import com.example.mydeskrobot.integration.input.DeferredInputQueue
import com.example.mydeskrobot.integration.input.InputPolicyEngine
import com.example.mydeskrobot.data.check.FireAndCheckRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskAlarmScheduler
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.domain.pending.PendingInboxKind
import com.example.mydeskrobot.data.context.RobotContextRepository
import com.example.mydeskrobot.data.body.BodySettings
import com.example.mydeskrobot.data.body.BodySettingsRepository
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettingsRepository
import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.integration.body.BodyApiResult
import com.example.mydeskrobot.integration.body.BodyExpressionContext
import com.example.mydeskrobot.integration.body.BodyExpressionController
import com.example.mydeskrobot.integration.body.BodyUrl
import com.example.mydeskrobot.data.input.InputSettingsRepository
import com.example.mydeskrobot.data.mood.MoodRepository
import com.example.mydeskrobot.data.workingmemory.WorkingMemoryRepository
import com.example.mydeskrobot.data.awareness.UserAwarenessRepository
import com.example.mydeskrobot.data.reflection.WeeklyStatsRepository
import com.example.mydeskrobot.domain.awareness.UserStateTracker
import com.example.mydeskrobot.domain.context.RobotContextPolicy
import com.example.mydeskrobot.domain.interaction.EyePokeSide
import com.example.mydeskrobot.domain.interaction.EyePokeTracker
import com.example.mydeskrobot.domain.mood.MoodManager
import com.example.mydeskrobot.domain.mood.MoodTrigger
import com.example.mydeskrobot.domain.mood.RobotMood
import com.example.mydeskrobot.domain.mood.UserInteractionTone
import com.example.mydeskrobot.domain.mood.UserInteractionToneDetector
import com.example.mydeskrobot.integration.mood.DelegatingMoodContextProvider
import com.example.mydeskrobot.integration.spatial.SpatialRuntimeBindings
import com.example.mydeskrobot.domain.spatial.SpatialIntentDetector
import com.example.mydeskrobot.domain.reflection.WeeklyStats
import com.example.mydeskrobot.integration.input.heartbeat.HeartbeatScheduler
import com.example.mydeskrobot.R
import com.example.mydeskrobot.data.speech.SttSettingsRepository
import com.example.mydeskrobot.data.speech.VoskModelManager
import com.example.mydeskrobot.domain.speech.SttProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val appContext: Context,
    private val speechRepository: SpeechToTextRepository,
    private val llmRepository: LlmRepository,
    private val ttsRepository: TextToSpeechRepository,
    private val wakePhrase: String,
    private val exitPhrase: String,
    private val postTtsCooldownMs: Long,
    private val boredIdleConfig: BoredIdleConfig,
    private val nightModeConfig: NightModeConfig,
    private val visionImageCapture: VisionImageCapture,
    private val messages: ConversationMessages,
    private var reasoningEngine: ReasoningEngine,
    private val llmSettingsRepository: LlmSettingsRepository,
    private val moodContextProvider: DelegatingMoodContextProvider,
    private val spatialBindings: SpatialRuntimeBindings,
    private val reasoningLogBuffer: ReasoningLogBuffer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConversationUiState(
            wakePhraseHint = messages.wakePhraseHint(wakePhrase),
            exitPhraseHint = messages.exitPhraseHint(exitPhrase),
            statusMessage = messages.idleStatus(),
        ),
    )
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private val _settingsUiState = MutableStateFlow(SettingsUiState())
    val settingsUiState: StateFlow<SettingsUiState> = _settingsUiState.asStateFlow()

    private var llmJob: Job? = null
    private var emotionTransitionJob: Job? = null
    private var eyePokeSquishJob: Job? = null
    private val eyePokeTracker = EyePokeTracker()
    private var nightModeMonitorJob: Job? = null
    private var lastAssistantResponse: String? = null
    /** Emozione suggerita dall'ultima risposta LLM (persiste fino al prossimo input utente). */
    private var lastLlmEmotion: RobotEmotion? = null
    private var ttsInterruptHandled = false
    /** Blocca nuove frasi STT mentre scatto + analisi visione sono in corso. */
    private var visionPipelineActive = false
    private var pendingVisionUserPhrase: String? = null
    /** Incrementa a ogni [sendPhraseToLlm]; i job obsoleti non devono resettare lo stato. */
    private var llmTurnGeneration = 0
    /** Frase ricevuta mentre il turno assistente è già in corso (race STT / doppio finalize). */
    private var queuedUtteranceForLlm: String? = null
    /** Ripristino cronologia se il turno LLM viene annullato prima della risposta robot. */
    private var conversationLogBeforeCurrentTurn: String? = null
    /** True while hotword orchestrator is in active voice session (not standby-only). */
    private var voiceSessionActive = false
    private val memoryRepository = UserMemoryRepository.create(appContext)
    private val listItemRepository = com.example.mydeskrobot.data.lists.ListItemRepository.create(appContext)
    private val memorySettingsRepository = MemorySettingsRepository(appContext)
    private val activityLogRepository = ActivityLogRepository.create(appContext)
    private val activityLogSettingsRepository = ActivityLogSettingsRepository(appContext)
    private val voskModelManager = VoskModelManager(appContext)
    private val sttSettingsRepository = SttSettingsRepository(appContext)
    private val deferredInputQueue = DeferredInputQueue()
    private val inputSettingsRepository = InputSettingsRepository(appContext)
    private val robotContextRepository = RobotContextRepository(appContext)
    private val fireAndCheckRepository = FireAndCheckRepository.create(appContext)
    private val scheduledTaskRepository = ScheduledTaskRepository.create(appContext)
    private val heartbeatSettingsRepository = HeartbeatSettingsRepository(appContext)
    private val bodySettingsRepository = BodySettingsRepository(appContext)
    /** Tracks body settings baked into [reasoningEngine]; refreshed before each LLM turn. */
    private var engineBodySettingsKey: String? = null
    /** True when the current LLM turn was triggered by a heartbeat input. */
    private var currentInputIsHeartbeat = false
    private val moodRepository = MoodRepository(appContext)
    private val moodManager = MoodManager(moodRepository, scope = viewModelScope)
    private val spatialContextManager by lazy { spatialBindings.bindManager(viewModelScope) }
    private val bodyExpressionController = BodyExpressionController(
        scope = viewModelScope,
        settingsProvider = { bodySettingsRepository.load() },
    )
    private var moodMonitorJob: Job? = null
    private val workingMemoryRepository = WorkingMemoryRepository(appContext)
    private val weeklyStatsRepository = WeeklyStatsRepository(appContext)
    private val userAwarenessRepository = UserAwarenessRepository(appContext)
    /** Timestamp of last proactive speak, for tracking response. */
    private var lastProactiveSpeakTime: Long? = null
    /** Last user phrase for working-memory topic extraction after a completed turn. */
    private var lastUserPhraseForTopic: String? = null
    /** Topic of last proactive speak, for attribution. */
    private var lastProactiveTopic: String? = null

    /** True while waiting for sì/no after [ReasoningResult.NeedsConfirmation]. */
    private var confirmationPending = false
    private var scheduledTaskIdForFireAndCheckCompletion: Long? = null
    private val memoryExtractionScheduler by lazy {
        val prompt = LlmPromptLoader.loadMemoryExtractorPrompt(appContext)
        val extractionClient = LlmClientFactory.create(runBlockingLoadSettings())
        val extractor = MemoryExtractionService(
            llmClient = extractionClient,
            memoryRepository = memoryRepository,
            extractorPrompt = prompt,
        )
        MemoryExtractionScheduler(
            scope = viewModelScope,
            settingsRepository = memorySettingsRepository,
            extractionService = extractor,
            memoryRepository = memoryRepository,
            getConversationLog = { _uiState.value.conversationLog },
            isStandby = {
                val state = _uiState.value
                state.isHotwordListeningActive && state.phase is ConversationPhase.WaitingForHotword
            },
            onExtractingChanged = { extracting ->
                _uiState.update { it.copy(isMemoryExtracting = extracting) }
            },
        )
    }

    private val activityLogExtractionScheduler by lazy {
        val extractorPrompt = LlmPromptLoader.loadActivityExtractorPrompt(appContext)
        val summaryPrompt = LlmPromptLoader.loadActivityHabitSummaryPrompt(appContext)
        val extractionClient = LlmClientFactory.create(runBlockingLoadSettings())
        val extractor = ActivityExtractionService(
            llmClient = extractionClient,
            activityLogRepository = activityLogRepository,
            extractorPrompt = extractorPrompt,
        )
        val summarizer = ActivityHabitSummarizer(
            llmClient = extractionClient,
            activityLogRepository = activityLogRepository,
            settingsRepository = activityLogSettingsRepository,
            summaryPrompt = summaryPrompt,
        )
        ActivityLogExtractionScheduler(
            scope = viewModelScope,
            settingsRepository = activityLogSettingsRepository,
            extractionService = extractor,
            habitSummarizer = summarizer,
            getConversationLog = { _uiState.value.conversationLog },
            isStandby = {
                val state = _uiState.value
                state.isHotwordListeningActive && state.phase is ConversationPhase.WaitingForHotword
            },
            isLlmConfigured = { reasoningEngine.isConfigured() },
        )
    }

    private val habitSummarizer by lazy {
        ActivityHabitSummarizer(
            llmClient = LlmClientFactory.create(runBlockingLoadSettings()),
            activityLogRepository = activityLogRepository,
            settingsRepository = activityLogSettingsRepository,
            summaryPrompt = LlmPromptLoader.loadActivityHabitSummaryPrompt(appContext),
        )
    }

    companion object {
        private const val TAG = "ConversationVM"
        private const val SURPRISED_FLASH_MS = 450L
        private const val INTERRUPT_SURPRISED_MS = 280L
        private const val ANGRY_RECOVERY_MS = 2_500L
        private const val NIGHT_MODE_RECHECK_MS = 60_000L
        private const val MOOD_RECHECK_MS = 30_000L
        /** Time window to consider user response as "positive" to proactive speak. */
        private const val PROACTIVE_RESPONSE_WINDOW_MS = 5 * 60_000L
        private const val EYE_SQUISH_HOLD_MS = 1_200L
    }

    init {
        moodContextProvider.snapshotProvider = { moodManager.currentMood.value }
        viewModelScope.launch {
            spatialContextManager.initialize()
        }
        viewModelScope.launch {
            HotwordEventDispatcher.events.collect { event ->
                when (event) {
                    is HotwordEvent.SessionStarted -> onSessionStarted(event.initialText)
                    is HotwordEvent.UtteranceInProgress -> onUtteranceInProgress(event.text)
                    is HotwordEvent.UtteranceReadyForLlm -> onUtteranceReadyForLlm(event.phrase)
                    is HotwordEvent.SpeechInterrupted -> onSpeechInterrupted(event.transcript)
                    is HotwordEvent.SessionEnded -> onSessionEnded(event.reason)
                    HotwordEvent.EngineStopped -> onHotwordEngineStopped()
                }
            }
        }
        viewModelScope.launch {
            SystemInputDispatcher.events.collect { event ->
                when (event) {
                    is SystemInputEvent.InputReceived -> onSystemInputReceived(event.envelope)
                }
            }
        }
        memoryExtractionScheduler.start()
        activityLogExtractionScheduler.start()
        viewModelScope.launch {
            activityLogRepository.pruneExpired()
            engineBodySettingsKey = bodySettingsKey(bodySettingsRepository.load())
        }
        viewModelScope.launch {
            robotContextRepository.observeEffectiveState().collect { state ->
                val profile = if (state.isNormal) RobotProfile.NORMAL else state.profile
                _uiState.update { it.copy(robotContextProfile = profile) }
            }
        }
        viewModelScope.launch {
            fireAndCheckRepository.observeActive().collect { entries ->
                _uiState.update { it.copy(pendingFireAndChecks = entries) }
            }
        }
        viewModelScope.launch {
            combine(
                scheduledTaskRepository.observePending(),
                deferredInputQueue.items,
            ) { reminders, deferred ->
                val merged = PendingInboxMapper.fromReminders(reminders) +
                    PendingInboxMapper.fromDeferredItems(deferred)
                merged.sortedBy { it.timeMillis }
            }.collect { merged ->
                val uiItems = merged.map { item ->
                    val kindLabel = when (item.kind) {
                        PendingInboxKind.REMINDER ->
                            appContext.getString(R.string.pending_inbox_kind_reminder)
                        PendingInboxKind.NOTIFICATION ->
                            appContext.getString(R.string.pending_inbox_kind_notification)
                    }
                    PendingInboxMapper.toUi(item, kindLabel)
                }
                _uiState.update { it.copy(pendingInboxItems = uiItems) }
            }
        }
        viewModelScope.launch {
            reasoningLogBuffer.displayText.collect { text ->
                _uiState.update { it.copy(reasoningLogText = text) }
            }
        }
    }

    fun onEvent(event: ConversationUiEvent) {
        when (event) {
            ConversationUiEvent.OnToggleHotwordListening -> toggleHotwordListening()
            ConversationUiEvent.OnBackgroundTapActivateListening -> onBackgroundTapActivateListening()
            is ConversationUiEvent.OnEyePoked -> onEyePoked(event.side)
            is ConversationUiEvent.OnCancelPendingInboxItem -> cancelPendingInboxItem(event.id)
            ConversationUiEvent.OnOpenSettings -> openSettings()
            ConversationUiEvent.OnDismissSettings -> dismissSettings()
            ConversationUiEvent.OnOpenLlmSettings -> openLlmSettings()
            ConversationUiEvent.OnDismissLlmSettings -> dismissLlmSettings()
            is ConversationUiEvent.OnLlmProviderChange -> updateLlmProvider(event.provider)
            is ConversationUiEvent.OnLlmFormChange -> updateLlmForm(event.form)
            ConversationUiEvent.OnSaveLlmSettings -> saveLlmSettings()
            ConversationUiEvent.OnTestLlmConnection -> testLlmConnection()
            ConversationUiEvent.OnOpenMemorySettings -> openMemorySettings()
            ConversationUiEvent.OnDismissMemorySettings -> dismissMemorySettings()
            is ConversationUiEvent.OnMemoryFormChange -> updateMemoryForm(event.form)
            ConversationUiEvent.OnSaveMemorySettings -> saveMemorySettings()
            ConversationUiEvent.OnResetMemoryManual -> resetMemoryManual()
            ConversationUiEvent.OnReorganizeMemoryManual -> reorganizeMemoryManual()
            is ConversationUiEvent.OnMemoryItemValueChange -> updateMemoryItemDraft(event.id, event.value)
            is ConversationUiEvent.OnSaveMemoryItem -> saveMemoryItem(event.id, event.value)
            is ConversationUiEvent.OnDeleteMemoryItem -> deleteMemoryItem(event.id)
            ConversationUiEvent.OnOpenSpatialSettings -> openSpatialSettings()
            ConversationUiEvent.OnDismissSpatialSettings -> dismissSpatialSettings()
            is ConversationUiEvent.OnSpatialPlaceLabelChange -> updateSpatialPlaceLabelDraft(event.id, event.label)
            is ConversationUiEvent.OnSpatialPlaceLandmarksChange -> updateSpatialPlaceLandmarksDraft(event.id, event.landmarks)
            is ConversationUiEvent.OnSaveSpatialPlace -> saveSpatialPlace(event.id, event.label, event.landmarks)
            is ConversationUiEvent.OnDeleteSpatialPlace -> deleteSpatialPlace(event.id)
            ConversationUiEvent.OnOpenLogDaySettings -> openLogDaySettings()
            ConversationUiEvent.OnDismissLogDaySettings -> dismissLogDaySettings()
            is ConversationUiEvent.OnLogDayFormChange -> updateLogDayForm(event.form)
            ConversationUiEvent.OnSaveLogDaySettings -> saveLogDaySettings()
            ConversationUiEvent.OnRefreshHabitSummary -> refreshHabitSummary()
            ConversationUiEvent.OnClearActivityLog -> clearActivityLog()
            ConversationUiEvent.OnClearReasoningLog -> reasoningLogBuffer.clear()
            ConversationUiEvent.OnOpenListSettings -> openListSettings()
            ConversationUiEvent.OnDismissListSettings -> dismissListSettings()
            is ConversationUiEvent.OnListItemValueChange -> updateListItemDraft(event.id, event.text)
            is ConversationUiEvent.OnListItemCheckedChange -> updateListItemCheckedDraft(event.id, event.checked)
            is ConversationUiEvent.OnSaveListItem -> saveListItem(event.id, event.text, event.checked)
            is ConversationUiEvent.OnDeleteListItem -> deleteListItem(event.id)
            ConversationUiEvent.OnOpenVoskModelSettings -> openVoskModelSettings()
            ConversationUiEvent.OnDismissVoskModelSettings -> dismissVoskModelSettings()
            ConversationUiEvent.OnDownloadVoskModel -> downloadVoskModel()
            ConversationUiEvent.OnSkipVoskModel -> skipVoskModel()
            ConversationUiEvent.OnOpenSttSettings -> openSttSettings()
            ConversationUiEvent.OnDismissSttSettings -> dismissSttSettings()
            is ConversationUiEvent.OnSttProviderChange -> updateSttProvider(event.provider)
            ConversationUiEvent.OnSaveSttSettings -> saveSttSettings()
            ConversationUiEvent.OnOpenNotificationSettings -> openNotificationSettings()
            ConversationUiEvent.OnDismissNotificationSettings -> dismissNotificationSettings()
            is ConversationUiEvent.OnNotificationEnabledChange -> updateNotificationEnabled(event.enabled)
            is ConversationUiEvent.OnNotificationPackageToggle -> toggleNotificationPackage(event.packageName)
            ConversationUiEvent.OnSaveNotificationSettings -> saveNotificationSettings()
            ConversationUiEvent.OnOpenHeartbeatSettings -> openHeartbeatSettings()
            ConversationUiEvent.OnDismissHeartbeatSettings -> dismissHeartbeatSettings()
            is ConversationUiEvent.OnHeartbeatFormChange -> updateHeartbeatForm(event.form)
            ConversationUiEvent.OnSaveHeartbeatSettings -> saveHeartbeatSettings()
            ConversationUiEvent.OnOpenBodySettings -> openBodySettings()
            ConversationUiEvent.OnDismissBodySettings -> dismissBodySettings()
            is ConversationUiEvent.OnBodyFormChange -> updateBodyForm(event.form)
            ConversationUiEvent.OnSaveBodySettings -> saveBodySettings()
            ConversationUiEvent.OnTestBodyConnection -> testBodyConnection()
            ConversationUiEvent.OnTestBodyMovement -> testBodyMovement()
        }
    }

    private suspend fun refreshPendingInbox() {
        val reminders = scheduledTaskRepository.listPending()
        val deferred = deferredInputQueue.snapshot()
        val merged = PendingInboxMapper.fromReminders(reminders) +
            PendingInboxMapper.fromDeferredItems(deferred)
        val uiItems = merged.sortedBy { it.timeMillis }.map { item ->
            val kindLabel = when (item.kind) {
                PendingInboxKind.REMINDER ->
                    appContext.getString(R.string.pending_inbox_kind_reminder)
                PendingInboxKind.NOTIFICATION ->
                    appContext.getString(R.string.pending_inbox_kind_notification)
            }
            PendingInboxMapper.toUi(item, kindLabel)
        }
        _uiState.update { it.copy(pendingInboxItems = uiItems) }
    }

    private fun cancelPendingInboxItem(id: String) {
        viewModelScope.launch {
            val reminderId = PendingInboxMapper.parseReminderId(id)
            if (reminderId != null) {
                if (scheduledTaskRepository.cancel(reminderId)) {
                    ScheduledTaskAlarmScheduler.cancel(appContext, reminderId)
                    fireAndCheckRepository.cancelByReminderId(reminderId)
                }
                return@launch
            }
            val dedupKey = PendingInboxMapper.parseDeferredDedupKey(id)
            if (dedupKey != null) {
                deferredInputQueue.removeByDedupKey(dedupKey)
            }
        }
    }

    private fun openSettings() {
        viewModelScope.launch {
            val currentProvider = sttSettingsRepository.getProvider()
            val notificationsEnabled = inputSettingsRepository.isNotificationsEnabled()
            val accessGranted = inputSettingsRepository.isNotificationAccessGranted()
            val allowedPackages = inputSettingsRepository.getAllowedPackages()
            refreshVoskModelState()
            _settingsUiState.update { 
                it.copy(
                    showMainDialog = true, 
                    feedbackMessage = null,
                    sttProvider = currentProvider,
                    notificationsEnabled = notificationsEnabled,
                    notificationAccessGranted = accessGranted,
                    notificationAllowedPackages = allowedPackages,
                ) 
            }
        }
    }

    private fun dismissSettings() {
        _settingsUiState.update { it.copy(showMainDialog = false) }
    }

    private fun openLlmSettings() {
        viewModelScope.launch {
            val current = llmSettingsRepository.load()
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showLlmDialog = true,
                    form = current.toFormState(),
                    feedbackMessage = null,
                )
            }
        }
    }

    private fun dismissLlmSettings() {
        _settingsUiState.update { it.copy(showLlmDialog = false, feedbackMessage = null) }
    }

    private fun openMemorySettings() {
        viewModelScope.launch {
            val settings = memorySettingsRepository.load()
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showMemoryDialog = true,
                    memoryForm = settings.toFormState(),
                    memoryEditItems = loadMemoryEditItems(),
                    feedbackMessage = null,
                )
            }
        }
    }

    private suspend fun loadMemoryEditItems(): List<MemoryItemUi> =
        memoryRepository.getUserFacingActive().map { it.toUi() }

    private fun dismissMemorySettings() {
        _settingsUiState.update { it.copy(showMemoryDialog = false, feedbackMessage = null) }
    }

    private fun updateMemoryForm(form: MemorySettingsFormState) {
        _settingsUiState.update { it.copy(memoryForm = form, feedbackMessage = null) }
    }

    private fun saveMemorySettings() {
        val form = _settingsUiState.value.memoryForm
        viewModelScope.launch {
            memorySettingsRepository.setEnabled(form.enabled)
            memorySettingsRepository.setIntervalSeconds(form.intervalSeconds)
            _settingsUiState.update {
                it.copy(
                    showMemoryDialog = false,
                    feedbackMessage = appContext.getString(R.string.memory_settings_saved),
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun resetMemoryManual() {
        viewModelScope.launch {
            memoryRepository.resetUserFacingMemory()
            memorySettingsRepository.setLastProcessedEntryCount(0L)
            _settingsUiState.update {
                it.copy(
                    memoryEditItems = emptyList(),
                    feedbackMessage = appContext.getString(R.string.memory_reset_done),
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun reorganizeMemoryManual() {
        viewModelScope.launch {
            val removed = memoryRepository.reorganize()
            _settingsUiState.update {
                it.copy(
                    memoryEditItems = loadMemoryEditItems(),
                    feedbackMessage = if (removed > 0) {
                        appContext.getString(R.string.memory_reorganize_done, removed)
                    } else {
                        appContext.getString(R.string.memory_reorganize_none)
                    },
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun openSpatialSettings() {
        viewModelScope.launch {
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showSpatialDialog = true,
                    spatialEditItems = loadSpatialEditItems(),
                    feedbackMessage = null,
                )
            }
        }
    }

    private suspend fun loadSpatialEditItems(): List<SpatialPlaceUi> =
        spatialBindings.placeRepository.listActive().map { place ->
            SpatialPlaceUi(
                id = place.id,
                label = place.label,
                roomType = place.roomType.name.lowercase(),
                landmarks = place.landmarks.joinToString(", "),
                description = place.description,
                lastSeenAt = place.lastSeenAt,
            )
        }

    private fun dismissSpatialSettings() {
        _settingsUiState.update { it.copy(showSpatialDialog = false, feedbackMessage = null) }
    }

    private fun updateSpatialPlaceLabelDraft(id: Long, label: String) {
        _settingsUiState.update { state ->
            state.copy(
                spatialEditItems = state.spatialEditItems.map { item ->
                    if (item.id == id) item.copy(label = label) else item
                },
            )
        }
    }

    private fun updateSpatialPlaceLandmarksDraft(id: Long, landmarks: String) {
        _settingsUiState.update { state ->
            state.copy(
                spatialEditItems = state.spatialEditItems.map { item ->
                    if (item.id == id) item.copy(landmarks = landmarks) else item
                },
            )
        }
    }

    private fun saveSpatialPlace(id: Long, label: String, landmarks: String) {
        viewModelScope.launch {
            val parsed = landmarks.split(",", ";")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val ok = spatialBindings.placeRepository.updateLabelAndLandmarks(
                id = id,
                label = label,
                landmarks = parsed,
                description = null,
            )
            _settingsUiState.update {
                it.copy(
                    spatialEditItems = if (ok) loadSpatialEditItems() else it.spatialEditItems,
                    feedbackMessage = if (ok) {
                        appContext.getString(R.string.spatial_place_saved)
                    } else {
                        appContext.getString(R.string.spatial_place_save_failed)
                    },
                    feedbackIsError = !ok,
                )
            }
        }
    }

    private fun deleteSpatialPlace(id: Long) {
        viewModelScope.launch {
            spatialBindings.placeRepository.softDelete(id)
            val snapshot = spatialContextManager.snapshot.value
            if (snapshot.currentPlaceId == id) {
                spatialContextManager.invalidateCurrentPlace()
            }
            _settingsUiState.update {
                it.copy(
                    spatialEditItems = loadSpatialEditItems(),
                    feedbackMessage = appContext.getString(R.string.spatial_place_deleted),
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun handleSpatialIntentBeforeLlm(phrase: String) {
        val detection = SpatialIntentDetector.detect(phrase)
        if (detection.shouldInvalidateCurrentPlace) {
            Log.i(TAG, "Spatial scene changed — invalidating current place")
            spatialContextManager.invalidateCurrentPlace()
        }
    }

    private fun openLogDaySettings() {
        viewModelScope.launch {
            val settings = activityLogSettingsRepository.load()
            val profile = activityLogRepository.getHabitSummary()
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showLogDayDialog = true,
                    logDayForm = settings.toLogDayFormState(
                        habitSummary = profile?.summaryText,
                        habitSummaryUpdatedAt = profile?.updatedAtMs,
                    ),
                    logDayGroups = loadLogDayGroups(),
                    feedbackMessage = null,
                )
            }
        }
    }

    private fun dismissLogDaySettings() {
        _settingsUiState.update { it.copy(showLogDayDialog = false, feedbackMessage = null) }
    }

    private fun updateLogDayForm(form: LogDaySettingsFormState) {
        _settingsUiState.update { it.copy(logDayForm = form, feedbackMessage = null) }
    }

    private fun saveLogDaySettings() {
        val form = _settingsUiState.value.logDayForm
        viewModelScope.launch {
            activityLogSettingsRepository.setEnabled(form.enabled)
            activityLogSettingsRepository.setIntervalMinutes(form.intervalMinutes)
            _settingsUiState.update {
                it.copy(
                    showLogDayDialog = false,
                    feedbackMessage = appContext.getString(R.string.log_day_settings_saved),
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun refreshHabitSummary() {
        viewModelScope.launch {
            _settingsUiState.update {
                it.copy(logDayForm = it.logDayForm.copy(isRefreshingSummary = true), feedbackMessage = null)
            }
            val ok = habitSummarizer.refreshSummary()
            val profile = activityLogRepository.getHabitSummary()
            _settingsUiState.update {
                it.copy(
                    logDayForm = it.logDayForm.copy(
                        isRefreshingSummary = false,
                        habitSummary = profile?.summaryText,
                        habitSummaryUpdatedAt = profile?.updatedAtMs,
                    ),
                    feedbackMessage = if (ok) {
                        appContext.getString(R.string.log_day_summary_updated)
                    } else {
                        appContext.getString(R.string.log_day_summary_failed)
                    },
                    feedbackIsError = !ok,
                )
            }
        }
    }

    private fun clearActivityLog() {
        viewModelScope.launch {
            activityLogRepository.clearAll()
            activityLogSettingsRepository.setLastProcessedEntryCount(0L)
            activityLogSettingsRepository.setLastSummaryAt(0L)
            activityLogSettingsRepository.setLastSummaryEventCount(0)
            _settingsUiState.update {
                it.copy(
                    logDayForm = it.logDayForm.copy(habitSummary = null, habitSummaryUpdatedAt = null),
                    logDayGroups = emptyList(),
                    feedbackMessage = appContext.getString(R.string.log_day_clear_done),
                    feedbackIsError = false,
                )
            }
        }
    }

    private suspend fun loadLogDayGroups(): List<DayActivityGroupUi> {
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY)
        val dayFormat = java.text.SimpleDateFormat("EEEE d MMM", java.util.Locale.ITALY)
        return activityLogRepository.getEventsGroupedByDay().map { group ->
            val dayLabel = runCatching {
                val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ITALY)
                    .parse(group.dayKey)
                if (parsed != null) dayFormat.format(parsed) else group.dayKey
            }.getOrDefault(group.dayKey)
            DayActivityGroupUi(
                dayLabel = dayLabel,
                events = group.events.map { event ->
                    ActivityLogItemUi(
                        id = event.id,
                        timeLabel = timeFormat.format(event.timestampMs),
                        label = event.label,
                        sourceLabel = activitySourceLabel(event.source),
                    )
                },
            )
        }
    }

    private fun activitySourceLabel(source: ActivitySource): String = when (source) {
        ActivitySource.EXTRACTOR -> appContext.getString(R.string.log_day_source_extractor)
        ActivitySource.TOOL -> appContext.getString(R.string.log_day_source_tool)
        ActivitySource.VOICE -> appContext.getString(R.string.log_day_source_voice)
    }

    private fun updateMemoryItemDraft(id: Long, value: String) {
        _settingsUiState.update { state ->
            state.copy(
                memoryEditItems = state.memoryEditItems.map { item ->
                    if (item.id == id) item.copy(value = value) else item
                },
                feedbackMessage = null,
            )
        }
    }

    private fun saveMemoryItem(id: Long, value: String) {
        viewModelScope.launch {
            val ok = memoryRepository.updateValue(id, value)
            _settingsUiState.update {
                it.copy(
                    memoryEditItems = if (ok) loadMemoryEditItems() else it.memoryEditItems,
                    feedbackMessage = if (ok) {
                        appContext.getString(R.string.memory_item_saved)
                    } else {
                        "Impossibile salvare la memoria"
                    },
                    feedbackIsError = !ok,
                )
            }
        }
    }

    private fun deleteMemoryItem(id: Long) {
        viewModelScope.launch {
            val ok = memoryRepository.deleteById(id)
            _settingsUiState.update {
                it.copy(
                    memoryEditItems = if (ok) loadMemoryEditItems() else it.memoryEditItems,
                    feedbackMessage = if (ok) {
                        appContext.getString(R.string.memory_item_deleted)
                    } else {
                        "Memoria non trovata"
                    },
                    feedbackIsError = !ok,
                )
            }
        }
    }

    private fun openListSettings() {
        viewModelScope.launch {
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showListDialog = true,
                    listEditItems = loadListEditItems(),
                    feedbackMessage = null,
                )
            }
        }
    }

    private fun dismissListSettings() {
        _settingsUiState.update { it.copy(showListDialog = false, feedbackMessage = null) }
    }

    private suspend fun loadListEditItems(): List<ListItemUi> =
        listItemRepository.list(limit = com.example.mydeskrobot.data.lists.ListItemRepository.MAX_LIMIT)
            .map { it.toListItemUi() }

    private fun updateListItemDraft(id: Long, text: String) {
        _settingsUiState.update { state ->
            state.copy(
                listEditItems = state.listEditItems.map { item ->
                    if (item.id == id) item.copy(text = text) else item
                },
                feedbackMessage = null,
            )
        }
    }

    private fun updateListItemCheckedDraft(id: Long, checked: Boolean) {
        _settingsUiState.update { state ->
            state.copy(
                listEditItems = state.listEditItems.map { item ->
                    if (item.id == id) item.copy(checked = checked) else item
                },
                feedbackMessage = null,
            )
        }
    }

    private fun saveListItem(id: Long, text: String, checked: Boolean) {
        viewModelScope.launch {
            val ok = listItemRepository.update(id, text = text, checked = checked)
            _settingsUiState.update {
                it.copy(
                    listEditItems = if (ok) loadListEditItems() else it.listEditItems,
                    feedbackMessage = if (ok) {
                        appContext.getString(R.string.list_item_saved)
                    } else {
                        appContext.getString(R.string.list_item_not_found)
                    },
                    feedbackIsError = !ok,
                )
            }
        }
    }

    private fun deleteListItem(id: Long) {
        viewModelScope.launch {
            val ok = listItemRepository.deleteById(id)
            _settingsUiState.update {
                it.copy(
                    listEditItems = if (ok) loadListEditItems() else it.listEditItems,
                    feedbackMessage = if (ok) {
                        appContext.getString(R.string.list_item_deleted)
                    } else {
                        appContext.getString(R.string.list_item_not_found)
                    },
                    feedbackIsError = !ok,
                )
            }
        }
    }

    private fun openVoskModelSettings() {
        refreshVoskModelState()
        _settingsUiState.update {
            it.copy(
                showMainDialog = false,
                showVoskModelDialog = true,
                feedbackMessage = null,
            )
        }
    }

    private fun dismissVoskModelSettings() {
        _settingsUiState.update { it.copy(showVoskModelDialog = false) }
    }

    private fun downloadVoskModel() {
        viewModelScope.launch {
            voskModelManager.state.collect { state ->
                _settingsUiState.update { it.copy(voskModelState = state) }
            }
        }
        viewModelScope.launch {
            voskModelManager.downloadModel()
        }
    }

    private fun skipVoskModel() {
        _settingsUiState.update { it.copy(showVoskModelDialog = false) }
    }

    private fun refreshVoskModelState() {
        val state = if (voskModelManager.isModelReady()) {
            VoskModelManager.ModelState.Ready(voskModelManager.getModelIfReady()!!)
        } else {
            VoskModelManager.ModelState.NotDownloaded
        }
        _settingsUiState.update { it.copy(voskModelState = state) }
    }

    fun isVoskModelReady(): Boolean = voskModelManager.isModelReady()

    private fun openSttSettings() {
        viewModelScope.launch {
            val currentProvider = sttSettingsRepository.getProvider()
            refreshVoskModelState()
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showSttDialog = true,
                    sttProvider = currentProvider,
                    feedbackMessage = null,
                )
            }
        }
    }

    private fun dismissSttSettings() {
        _settingsUiState.update { it.copy(showSttDialog = false, feedbackMessage = null) }
    }

    private fun updateSttProvider(provider: SttProvider) {
        _settingsUiState.update { it.copy(sttProvider = provider, feedbackMessage = null) }
    }

    private fun saveSttSettings() {
        val provider = _settingsUiState.value.sttProvider
        viewModelScope.launch {
            sttSettingsRepository.setProvider(provider)
            _settingsUiState.update {
                it.copy(
                    showSttDialog = false,
                    feedbackMessage = appContext.getString(R.string.stt_settings_saved),
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun openNotificationSettings() {
        viewModelScope.launch {
            val enabled = inputSettingsRepository.isNotificationsEnabled()
            val accessGranted = inputSettingsRepository.isNotificationAccessGranted()
            val allowedPackages = inputSettingsRepository.getAllowedPackages()
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showNotificationDialog = true,
                    notificationsEnabled = enabled,
                    notificationAccessGranted = accessGranted,
                    notificationAllowedPackages = allowedPackages,
                    feedbackMessage = null,
                )
            }
        }
    }

    private fun dismissNotificationSettings() {
        _settingsUiState.update { it.copy(showNotificationDialog = false) }
    }

    private fun updateNotificationEnabled(enabled: Boolean) {
        _settingsUiState.update { it.copy(notificationsEnabled = enabled, feedbackMessage = null) }
    }

    private fun toggleNotificationPackage(packageName: String) {
        val current = _settingsUiState.value.notificationAllowedPackages.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _settingsUiState.update { it.copy(notificationAllowedPackages = current, feedbackMessage = null) }
    }

    private fun saveNotificationSettings() {
        val state = _settingsUiState.value
        viewModelScope.launch {
            inputSettingsRepository.setNotificationsEnabled(state.notificationsEnabled)
            inputSettingsRepository.setAllowedPackages(state.notificationAllowedPackages)
            val accessGranted = inputSettingsRepository.isNotificationAccessGranted()
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showNotificationDialog = false,
                    notificationAccessGranted = accessGranted,
                    feedbackMessage = appContext.getString(R.string.notification_settings_saved),
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun openHeartbeatSettings() {
        viewModelScope.launch {
            val settings = heartbeatSettingsRepository.load()
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showHeartbeatDialog = true,
                    heartbeatForm = settings.toFormState(),
                    feedbackMessage = null,
                )
            }
        }
    }

    private fun dismissHeartbeatSettings() {
        _settingsUiState.update {
            it.copy(showHeartbeatDialog = false, showMainDialog = true)
        }
    }

    private fun updateHeartbeatForm(form: HeartbeatSettingsFormState) {
        _settingsUiState.update { it.copy(heartbeatForm = form, feedbackMessage = null) }
    }

    private fun openBodySettings() {
        viewModelScope.launch {
            val settings = bodySettingsRepository.load()
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showBodyDialog = true,
                    bodyForm = settings.toFormState(),
                    bodyTesting = false,
                    feedbackMessage = null,
                )
            }
        }
    }

    private fun dismissBodySettings() {
        _settingsUiState.update {
            it.copy(showBodyDialog = false, showMainDialog = true, bodyTesting = false, feedbackMessage = null)
        }
    }

    private fun updateBodyForm(form: BodySettingsFormState) {
        _settingsUiState.update { it.copy(bodyForm = form, feedbackMessage = null) }
    }

    private fun saveBodySettings() {
        val form = _settingsUiState.value.bodyForm
        val normalizedUrl = BodyUrl.normalize(form.baseUrl)
        if (form.enabled && normalizedUrl.isBlank()) {
            _settingsUiState.update {
                it.copy(
                    feedbackMessage = appContext.getString(R.string.body_url_required),
                    feedbackIsError = true,
                )
            }
            return
        }

        viewModelScope.launch {
            bodySettingsRepository.save(enabled = form.enabled, baseUrl = normalizedUrl)
            val llmSettings = llmSettingsRepository.load()
            val appliedNow = reloadReasoningEngine(llmSettings)
            val message = if (appliedNow) {
                appContext.getString(R.string.body_settings_saved)
            } else {
                appContext.getString(R.string.llm_save_deferred)
            }
            _settingsUiState.update {
                it.copy(
                    showBodyDialog = false,
                    showMainDialog = true,
                    bodyForm = form.copy(baseUrl = normalizedUrl),
                    feedbackMessage = message,
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun testBodyConnection() {
        val form = _settingsUiState.value.bodyForm
        val normalizedUrl = BodyUrl.normalize(form.baseUrl)
        if (normalizedUrl.isBlank()) {
            _settingsUiState.update {
                it.copy(
                    feedbackMessage = appContext.getString(R.string.body_url_required),
                    feedbackIsError = true,
                )
            }
            return
        }

        viewModelScope.launch {
            _settingsUiState.update { it.copy(bodyTesting = true, feedbackMessage = null) }
            val client = BodyApiClient(normalizedUrl)
            when (val result = client.getStatus()) {
                is BodyApiResult.Success -> {
                    val status = result.data
                    val ip = status.urlIp ?: status.ip ?: normalizedUrl
                    val rssi = status.rssi?.toString() ?: "—"
                    val moving = if (status.moving) {
                        appContext.getString(R.string.body_status_moving_yes)
                    } else {
                        appContext.getString(R.string.body_status_moving_no)
                    }
                    val updatedUrl = BodyUrl.normalize(status.urlIp.orEmpty()).ifBlank { normalizedUrl }
                    _settingsUiState.update { state ->
                        state.copy(
                            bodyForm = state.bodyForm.copy(baseUrl = updatedUrl),
                            bodyTesting = false,
                            feedbackMessage = appContext.getString(
                                R.string.body_test_connection_ok,
                                ip,
                                rssi,
                                moving,
                            ),
                            feedbackIsError = false,
                        )
                    }
                }
                is BodyApiResult.Error -> {
                    _settingsUiState.update {
                        it.copy(
                            bodyTesting = false,
                            feedbackMessage = appContext.getString(
                                R.string.body_test_connection_failed,
                                result.message,
                            ),
                            feedbackIsError = true,
                        )
                    }
                }
            }
        }
    }

    private fun testBodyMovement() {
        val form = _settingsUiState.value.bodyForm
        val normalizedUrl = BodyUrl.normalize(form.baseUrl)
        if (normalizedUrl.isBlank()) {
            _settingsUiState.update {
                it.copy(
                    feedbackMessage = appContext.getString(R.string.body_url_required),
                    feedbackIsError = true,
                )
            }
            return
        }

        viewModelScope.launch {
            _settingsUiState.update { it.copy(bodyTesting = true, feedbackMessage = null) }
            val client = BodyApiClient(normalizedUrl)
            when (val result = client.runTest(speed = 40)) {
                is BodyApiResult.Success -> {
                    _settingsUiState.update {
                        it.copy(
                            bodyTesting = false,
                            feedbackMessage = appContext.getString(R.string.body_test_movement_ok),
                            feedbackIsError = false,
                        )
                    }
                }
                is BodyApiResult.Error -> {
                    _settingsUiState.update {
                        it.copy(
                            bodyTesting = false,
                            feedbackMessage = appContext.getString(
                                R.string.body_test_movement_failed,
                                result.message,
                            ),
                            feedbackIsError = true,
                        )
                    }
                }
            }
        }
    }

    private fun saveHeartbeatSettings() {
        val form = _settingsUiState.value.heartbeatForm
        viewModelScope.launch {
            heartbeatSettingsRepository.update(
                enabled = form.enabled,
                intervalMinutes = form.intervalMinutes,
                startHour = form.startHour,
                endHour = form.endHour,
                proactiveThreshold = form.proactiveThreshold,
            )

            if (form.enabled && _uiState.value.isHotwordListeningActive) {
                HeartbeatScheduler.schedule(appContext, form.intervalMinutes)
            } else {
                HeartbeatScheduler.cancel(appContext)
            }

            _settingsUiState.update {
                it.copy(
                    showHeartbeatDialog = false,
                    showMainDialog = true,
                    feedbackMessage = appContext.getString(R.string.heartbeat_settings_saved),
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun updateLlmProvider(provider: com.example.mydeskrobot.domain.llm.LlmProvider) {
        _settingsUiState.update { state ->
            val form = state.form.copy(provider = provider)
            val withDefaults = when (provider) {
                com.example.mydeskrobot.domain.llm.LlmProvider.GEMINI -> {
                    if (form.textModel.isBlank()) {
                        form.copy(textModel = "gemini-2.0-flash")
                    } else {
                        form
                    }
                }
                com.example.mydeskrobot.domain.llm.LlmProvider.LM_STUDIO -> form
            }
            state.copy(form = withDefaults, feedbackMessage = null)
        }
    }

    private fun updateLlmForm(form: LlmSettingsFormState) {
        _settingsUiState.update { it.copy(form = form, feedbackMessage = null) }
    }

    private fun saveLlmSettings() {
        val form = _settingsUiState.value.form
        val settings = form.toDomain()
        val validationError = llmSettingsRepository.validationError(settings)
        if (validationError != null) {
            _settingsUiState.update {
                it.copy(feedbackMessage = validationError, feedbackIsError = true)
            }
            return
        }

        viewModelScope.launch {
            _settingsUiState.update { it.copy(isSaving = true, feedbackMessage = null) }
            llmSettingsRepository.save(settings)

            val appliedNow = reloadReasoningEngine(settings)
            val message = if (appliedNow) {
                appContext.getString(R.string.llm_save_success)
            } else {
                appContext.getString(R.string.llm_save_deferred)
            }

            _settingsUiState.update {
                it.copy(
                    isSaving = false,
                    showLlmDialog = false,
                    feedbackMessage = message,
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun testLlmConnection() {
        val form = _settingsUiState.value.form
        val settings = form.toDomain()
        val validationError = llmSettingsRepository.validationError(settings)
        if (validationError != null) {
            _settingsUiState.update {
                it.copy(feedbackMessage = validationError, feedbackIsError = true)
            }
            return
        }

        viewModelScope.launch {
            _settingsUiState.update { it.copy(isTesting = true, feedbackMessage = null) }
            val client = LlmClientFactory.create(settings)
            val result = client.chat(
                messages = listOf(ConversationMessage.User("ping")),
                systemPrompt = "Reply with the single word OK.",
            )
            val message = result.fold(
                onSuccess = { appContext.getString(R.string.llm_test_success) },
                onFailure = { error ->
                    appContext.getString(R.string.llm_test_failed, LlmHttpErrors.formatForLog(error))
                },
            )
            _settingsUiState.update {
                it.copy(
                    isTesting = false,
                    feedbackMessage = message,
                    feedbackIsError = result.isFailure,
                )
            }
        }
    }

    /**
     * @return true if the new engine is active immediately
     */
    private fun reloadReasoningEngine(settings: LlmSettings): Boolean {
        if (isAssistantTurnInProgress()) {
            return false
        }
        confirmationPending = false
        reasoningEngine.reset()
        reasoningEngine = ReasoningModule.createReasoningEngine(
            context = appContext,
            visionImageCapture = visionImageCapture,
            llmSettings = settings,
            moodContextProvider = moodContextProvider,
            spatialBindings = spatialBindings,
            reasoningLogObserver = reasoningLogBuffer,
        )
        engineBodySettingsKey = bodySettingsKey(runBlocking { bodySettingsRepository.load() })
        return true
    }

    private fun bodySettingsKey(settings: BodySettings): String =
        "${settings.enabled}|${settings.baseUrl}"

    private suspend fun refreshReasoningEngineIfBodySettingsChanged() {
        val key = bodySettingsKey(bodySettingsRepository.load())
        if (key == engineBodySettingsKey) return
        val settings = llmSettingsRepository.load()
        reloadReasoningEngine(settings)
    }

    private fun toggleHotwordListening() {
        if (_uiState.value.isHotwordListeningActive) {
            disableHotwordListening()
        } else {
            enableHotwordListening()
        }
    }

    /**
     * Tap on the main background (not controls): opens voice session like wake word.
     */
    private fun onBackgroundTapActivateListening() {
        val state = _uiState.value
        if (!state.isHotwordListeningActive) return
        if (state.phase !is ConversationPhase.WaitingForHotword) return
        if (voiceSessionActive) return
        if (isAssistantTurnInProgress()) return
        if (!HotwordController.isRunning()) return

        HotwordController.activateVoiceSession()
    }

    /**
     * User poked an eye: close it briefly; repeated pokes escalate annoyance → anger.
     */
    private fun onEyePoked(side: EyePokeSide) {
        if (!_uiState.value.isHotwordListeningActive) return

        val reaction = eyePokeTracker.recordPoke()
        val count = eyePokeTracker.recentPokeCount()
        moodManager.recordEyePoke(reaction.tier, count)

        _uiState.update {
            it.copy(
                eyeSquishLeft = it.eyeSquishLeft || side == EyePokeSide.LEFT,
                eyeSquishRight = it.eyeSquishRight || side == EyePokeSide.RIGHT,
            )
        }

        scheduleEyeSquishRelease()
    }

    private fun scheduleEyeSquishRelease() {
        eyePokeSquishJob?.cancel()
        eyePokeSquishJob = viewModelScope.launch {
            delay(EYE_SQUISH_HOLD_MS)
            _uiState.update {
                it.copy(eyeSquishLeft = false, eyeSquishRight = false)
            }
        }
    }

    fun enableHotwordListening() {
        if (_uiState.value.isHotwordListeningActive) return

        if (!speechRepository.isAvailable()) {
            showError(messages.sttUnavailable())
            return
        }

        if (!reasoningEngine.isConfigured()) {
            showError(messages.llmNotConfigured())
            return
        }

        lastAssistantResponse = null
        lastLlmEmotion = null
        voiceSessionActive = false
        val night = isNightModeNow()
        HotwordServiceStarter.start(appContext)
        viewModelScope.launch {
            memorySettingsRepository.setLastProcessedEntryCount(0L)
        }
        _uiState.update {
            it.copy(
                phase = ConversationPhase.WaitingForHotword,
                emotion = standbyEmotionFor(night),
                statusMessage = standbyStatusFor(night),
                isHotwordListeningActive = true,
                isNightMode = night,
                conversationLog = "",
                currentUtterance = "",
            )
        }
        startNightModeMonitor()
        startMoodMonitor()
        memoryExtractionScheduler.start()
        activityLogExtractionScheduler.start()
        startHeartbeatIfEnabled()
    }

    private fun startHeartbeatIfEnabled() {
        viewModelScope.launch {
            val settings = heartbeatSettingsRepository.load()
            if (settings.enabled) {
                HeartbeatScheduler.schedule(appContext, settings.intervalMinutes)
            }
        }
    }

    fun disableHotwordListening() {
        llmTurnGeneration++
        llmJob?.cancel()
        emotionTransitionJob?.cancel()
        eyePokeSquishJob?.cancel()
        queuedUtteranceForLlm = null
        voiceSessionActive = false
        clearVisionPipeline()
        stopNightModeMonitor()
        stopMoodMonitor()
        ttsRepository.stop()
        HotwordServiceStarter.stop(appContext)
        HeartbeatScheduler.cancel(appContext)
        _uiState.update {
            it.copy(
                phase = ConversationPhase.Idle,
                emotion = RobotEmotion.NEUTRAL,
                statusMessage = messages.idleStatus(),
                isHotwordListeningActive = false,
                currentUtterance = "",
                eyeSquishLeft = false,
                eyeSquishRight = false,
            )
        }
        memoryExtractionScheduler.stop()
        activityLogExtractionScheduler.stop()
    }

    private fun showError(message: String) {
        emotionTransitionJob?.cancel()
        _uiState.update {
            it.copy(
                phase = ConversationPhase.Error(message),
                emotion = RobotEmotion.ANGRY,
                statusMessage = message,
                isHotwordListeningActive = false,
            )
        }
    }

    private fun onHotwordEngineStopped() {
        if (!_uiState.value.isHotwordListeningActive) return
        llmJob?.cancel()
        ttsRepository.stop()
        showError(messages.hotwordEngineStopped())
    }

    private fun onSessionStarted(initialText: String?) {
        if (!_uiState.value.isHotwordListeningActive) return
        voiceSessionActive = true

        val initial = initialText?.trim().orEmpty()

        _uiState.update {
            it.copy(
                phase = ConversationPhase.ActiveListening,
                emotion = RobotEmotion.SURPRISED,
                statusMessage = messages.activeListeningStatus(exitPhrase),
                currentUtterance = initial,
            )
        }

        val night = isNightModeNow()
        _uiState.update { it.copy(isNightMode = night) }

        emotionTransitionJob?.cancel()
        emotionTransitionJob = viewModelScope.launch {
            delay(SURPRISED_FLASH_MS)
            if (!_uiState.value.isHotwordListeningActive) return@launch
            if (_uiState.value.phase !is ConversationPhase.ActiveListening) return@launch
            refreshUiEmotionFromMood()
        }
    }

    private fun onUtteranceInProgress(text: String) {
        Log.d(TAG, "onUtteranceInProgress: '${text.take(40)}' (phase=${_uiState.value.phase::class.simpleName})")
        if (!_uiState.value.isHotwordListeningActive) return
        if (isAssistantTurnInProgress()) {
            Log.d(TAG, "onUtteranceInProgress: assistant turn in progress, skipping UI update")
            return
        }

        emotionTransitionJob?.cancel()
        lastLlmEmotion = null
        _uiState.update {
            it.copy(
                phase = ConversationPhase.ActiveListening,
                currentUtterance = text.trim(),
                statusMessage = messages.activeListeningStatus(exitPhrase),
                emotion = deriveDisplayEmotion(
                    mood = moodManager.currentMood.value,
                    phase = ConversationPhase.ActiveListening,
                    isNightMode = it.isNightMode,
                ),
            )
        }
    }

    private fun onUtteranceReadyForLlm(phrase: String) {
        Log.i(TAG, "onUtteranceReadyForLlm: '${phrase.take(60)}'")
        if (!_uiState.value.isHotwordListeningActive) {
            Log.d(TAG, "onUtteranceReadyForLlm: ignored (hotword session inactive)")
            return
        }

        var trimmed = phrase.trim()
        if (trimmed.isEmpty()) {
            clearCurrentUtteranceDisplay()
            return
        }
        trimmed = EchoSpeechFilter.stripLeadingAssistantEcho(trimmed, lastAssistantResponse)
        if (trimmed.isEmpty()) {
            Log.d(TAG, "onUtteranceReadyForLlm: discarded (empty after echo strip)")
            clearCurrentUtteranceDisplay()
            return
        }
        if (EchoSpeechFilter.isLikelyAssistantEcho(trimmed, lastAssistantResponse)) {
            Log.d(TAG, "onUtteranceReadyForLlm: discarded (assistant echo)")
            clearCurrentUtteranceDisplay()
            return
        }
        if (EchoSpeechFilter.isLikelyAssistantEcho(trimmed, LlmRepositoryImpl.DEFAULT_IMAGE_ACK)) {
            Log.d(TAG, "onUtteranceReadyForLlm: discarded (image ack echo)")
            clearCurrentUtteranceDisplay()
            return
        }
        pendingVisionUserPhrase?.let { original ->
            if (EchoSpeechFilter.isLikelyAssistantEcho(trimmed, original)) {
                Log.d(TAG, "onUtteranceReadyForLlm: discarded (vision phrase echo)")
                clearCurrentUtteranceDisplay()
                return
            }
        }

        if (handleMemoryVoiceCommand(trimmed)) {
            clearCurrentUtteranceDisplay()
            return
        }

        if (isAssistantTurnInProgress() || visionPipelineActive) {
            Log.d(TAG, "onUtteranceReadyForLlm: queued (assistant turn in progress)")
            queueUtteranceForLlm(trimmed)
            return
        }

        Log.d(TAG, "onUtteranceReadyForLlm: sendPhraseToLlm")
        sendPhraseToLlm(trimmed)
    }

    private fun clearCurrentUtteranceDisplay() {
        _uiState.update { state ->
            if (state.currentUtterance.isEmpty()) state
            else state.copy(currentUtterance = "")
        }
    }

    private fun handleMemoryVoiceCommand(phrase: String): Boolean {
        val normalized = phrase.trim().lowercase()
        val command = when {
            normalized == "cosa sai di me" || normalized.startsWith("cosa sai di me ") -> "show"
            normalized == "reset memoria" -> "reset"
            normalized.startsWith("dimentica ") -> "forget"
            else -> null
        } ?: return false

        viewModelScope.launch {
            when (command) {
                "show" -> {
                    val facts = memoryRepository.getUserFacingActive().take(8)
                    val reply = if (facts.isEmpty()) {
                        "Per ora non ho memorie personali salvate."
                    } else {
                        facts.joinToString(
                            prefix = "Questo è quello che ricordo: ",
                            separator = "; ",
                        ) { it.value }
                    }
                    speakMemoryCommandReply(phrase, reply)
                }
                "reset" -> {
                    memoryRepository.resetUserFacingMemory()
                    memorySettingsRepository.setLastProcessedEntryCount(0L)
                    speakMemoryCommandReply(phrase, "Ho cancellato la memoria personale. Gli obiettivi interni del robot restano attivi.")
                }
                "forget" -> {
                    val text = phrase.substringAfter("dimentica", "").trim()
                    val result = if (text.isBlank()) {
                        ForgetByTopicResult(0, emptyList(), emptyList())
                    } else {
                        memoryRepository.forgetByTopic(text)
                    }
                    val reply = when {
                        result.deletedCount == 0 ->
                            "Non ho trovato memorie su \"$text\" da dimenticare."
                        result.deletedCount == 1 ->
                            "Fatto, ho dimenticato una memoria su $text."
                        else ->
                            "Fatto, ho dimenticato ${result.deletedCount} memorie su $text."
                    }
                    speakMemoryCommandReply(phrase, reply)
                }
            }
        }
        return true
    }

    private suspend fun speakMemoryCommandReply(userPhrase: String, robotReply: String) {
        if (!_uiState.value.isHotwordListeningActive) return
        HotwordController.beginAssistantTurn()
        val currentLog = _uiState.value.conversationLog
        _uiState.update {
            it.copy(
                phase = ConversationPhase.Thinking,
                emotion = RobotEmotion.THINKING,
                statusMessage = messages.thinkingStatus(),
                currentUtterance = "",
                conversationLog = appendRobotLine(appendUserLine(currentLog, userPhrase), robotReply),
            )
        }
        lastAssistantResponse = robotReply
        speakResponse(robotReply, RobotEmotion.NEUTRAL)
    }

    private fun queueUtteranceForLlm(phrase: String) {
        queuedUtteranceForLlm = phrase
    }

    private fun drainQueuedUtterance() {
        val next = queuedUtteranceForLlm?.trim().orEmpty()
        queuedUtteranceForLlm = null
        if (next.isEmpty()) return
        if (!_uiState.value.isHotwordListeningActive) return
        if (isAssistantTurnInProgress() || visionPipelineActive) return
        sendPhraseToLlm(next)
    }

    private fun sendPhraseToLlm(phrase: String) {
        if (confirmationPending) {
            when (VoiceConfirmationMatcher.parse(phrase)) {
                VoiceConfirmationDecision.YES -> {
                    processConfirmationResponse(confirmed = true, phrase = phrase)
                    return
                }
                VoiceConfirmationDecision.NO -> {
                    processConfirmationResponse(confirmed = false, phrase = phrase)
                    return
                }
                VoiceConfirmationDecision.UNCLEAR -> {
                    viewModelScope.launch {
                        deliverAssistantReply(
                            LlmAssistantReply(
                                text = "Non ho capito. Dimmi sì per procedere o no per annullare.",
                                emotion = RobotEmotion.CONFUSED,
                                imageRequired = false,
                            ),
                        )
                    }
                    return
                }
                VoiceConfirmationDecision.NOT_CONFIRMATION -> {
                    confirmationPending = false
                    reasoningEngine.cancelPendingConfirmation()
                }
            }
        }

        val turnId = ++llmTurnGeneration
        llmJob?.cancel()
        emotionTransitionJob?.cancel()
        HotwordController.beginAssistantTurn()
        lastUserPhraseForTopic = phrase

        viewModelScope.launch { heartbeatSettingsRepository.recordInteraction() }
        viewModelScope.launch { workingMemoryRepository.recordInteraction() }
        viewModelScope.launch {
            weeklyStatsRepository.recordInteraction()
            checkProactiveSpeakResponse()
        }
        viewModelScope.launch {
            userAwarenessRepository.update { current ->
                UserStateTracker.analyzeUserText(phrase, current)
            }
        }
        applyMoodTriggerForUserPhrase(phrase)
        handleSpatialIntentBeforeLlm(phrase)

        val state = _uiState.value
        conversationLogBeforeCurrentTurn = state.conversationLog
        _uiState.update {
            it.copy(
                phase = ConversationPhase.Thinking,
                emotion = RobotEmotion.THINKING,
                statusMessage = messages.thinkingStatus(),
                currentUtterance = "",
                conversationLog = appendUserLine(state.conversationLog, phrase),
            )
        }

        llmJob = viewModelScope.launch {
            try {
                if (turnId != llmTurnGeneration) return@launch
                refreshReasoningEngineIfBodySettingsChanged()

                val result = reasoningEngine.processUserInput(
                    userText = phrase,
                    onIntermediateResponse = { intermediate ->
                        if (turnId != llmTurnGeneration) return@processUserInput
                        if (!_uiState.value.isHotwordListeningActive) return@processUserInput
                        handleIntermediateResponse(intermediate)
                    },
                )

                if (turnId != llmTurnGeneration) return@launch
                if (!_uiState.value.isHotwordListeningActive) return@launch

                handleReasoningResult(result)
            } catch (e: CancellationException) {
                if (turnId == llmTurnGeneration) {
                    recoverFromCancelledLlmTurn()
                }
                throw e
            }
        }
    }

    /**
     * Translates intermediate responses from the reasoning engine into UI state updates
     * and optional TTS for preliminary replies before tool execution.
     */
    private suspend fun handleIntermediateResponse(intermediate: IntermediateResponse) {
        if (intermediate.isToolExecuting) {
            val toolStatus = toolStatusMessage(intermediate.toolName)
            val toolPhase = toolPhaseFor(intermediate.toolName)
            _uiState.update {
                it.copy(
                    phase = toolPhase,
                    emotion = RobotEmotion.THINKING,
                    statusMessage = toolStatus,
                )
            }
            return
        }

        if (intermediate.text.isBlank()) return

        if (intermediate.suppressIntermediateSpeech) return

        val suppressSpeech = intermediate.speakConfidence != null && intermediate.speakConfidence <= 0.0
        if (suppressSpeech) return

        val emotion = LlmEmotionMapper.fromLlmValue(intermediate.emotion)
        speakIntermediate(intermediate.text, emotion)
    }

    /**
     * Speaks a preliminary reply (e.g. "Ok, do un'occhiata") and returns
     * the UI to Thinking afterwards so the tool execution status can show.
     * Keeps HotwordController stack balanced even on TTS failure.
     */
    private suspend fun speakIntermediate(text: String, emotion: RobotEmotion?) {
        if (!_uiState.value.isHotwordListeningActive) return

        ttsInterruptHandled = false
        lastAssistantResponse = text
        _uiState.update {
            it.copy(
                phase = ConversationPhase.Speaking,
                emotion = emotion ?: RobotEmotion.SPEAKING,
                statusMessage = messages.speakingStatus(exitPhrase),
                conversationLog = appendRobotLine(it.conversationLog, text),
            )
        }

        val textForTts = MarkdownStripper.strip(text)
        try {
            ttsRepository.speak(textForTts).onFailure { error ->
                if (error !is TtsInterruptedException) {
                    // Don't switch to recoverable anger here: the chain will continue.
                }
            }
        } finally {
            // Keep STT paused for upcoming tool execution / next LLM turn.
            HotwordController.beginAssistantTurn()
        }

        if (!_uiState.value.isHotwordListeningActive) return
        _uiState.update {
            it.copy(
                phase = ConversationPhase.Thinking,
                emotion = RobotEmotion.THINKING,
                statusMessage = messages.thinkingStatus(),
            )
        }
    }

    private fun toolPhaseFor(toolName: String?): ConversationPhase {
        return when (toolName) {
            "take_photo", "detect_presence" -> ConversationPhase.CapturingImage
            else -> ConversationPhase.Thinking
        }
    }

    private fun toolStatusMessage(toolName: String?): String {
        return when (toolName) {
            "take_photo", "detect_presence" -> messages.capturingImageStatus()
            "open_browser" -> messages.openingBrowserStatus()
            "dial_phone" -> messages.openingDialerStatus()
            "play_spotify" -> messages.openingSpotifyStatus()
            "set_robot_context" -> messages.settingRobotContextStatus()
            "web_search" -> messages.webSearchStatus()
            "fetch_url" -> messages.fetchUrlStatus()
            else -> messages.thinkingStatus()
        }
    }

    /**
     * Handles the final result from the reasoning engine.
     */
    private suspend fun handleReasoningResult(result: ReasoningResult) {
        when (result) {
            is ReasoningResult.Success -> {
                if (shouldSuppressHeartbeat(result)) {
                    Log.i(TAG, "Heartbeat suppressed: confidence ${result.speakConfidence} below threshold")
                    currentInputIsHeartbeat = false
                    finalizeTurnWithoutSpeech(LlmEmotionMapper.fromLlmValue(result.emotion))
                    return
                }
                val wasHeartbeat = currentInputIsHeartbeat
                currentInputIsHeartbeat = false

                if (!wasHeartbeat) {
                    moodManager.recordTaskCompletedUseful()
                    recordTopicFromUserTurn()
                    lastUserPhraseForTopic?.let { phrase ->
                        viewModelScope.launch {
                            fireAndCheckRepository.enrichLatestTriggerReason(phrase)
                        }
                    }
                }

                scheduledTaskIdForFireAndCheckCompletion?.let { taskId ->
                    viewModelScope.launch {
                        fireAndCheckRepository.completeAfterVerificationHandled(taskId)
                    }
                    scheduledTaskIdForFireAndCheckCompletion = null
                }

                refreshPendingInbox()

                if (result.finalText.isBlank()) {
                    finalizeTurnWithoutSpeech(LlmEmotionMapper.fromLlmValue(result.emotion))
                } else {
                    if (wasHeartbeat) {
                        viewModelScope.launch {
                            workingMemoryRepository.recordProactiveSpeak()
                            weeklyStatsRepository.recordProactiveSpeak()
                        }
                        markProactiveSpeakForTracking(extractTopicFromText(result.finalText))
                    }
                    val reply = LlmAssistantReply(
                        text = result.finalText,
                        emotion = LlmEmotionMapper.fromLlmValue(result.emotion),
                        imageRequired = false,
                    )
                    deliverAssistantReply(reply)
                }
            }

            is ReasoningResult.Error -> {
                currentInputIsHeartbeat = false
                handleLlmFailure(result.message)
            }

            is ReasoningResult.MaxStepsReached -> {
                currentInputIsHeartbeat = false
                val text = result.lastText.ifBlank { messages.emptyReplyError() }
                val reply = LlmAssistantReply(text = text, emotion = null, imageRequired = false)
                deliverAssistantReply(reply)
            }

            is ReasoningResult.NeedsConfirmation -> {
                currentInputIsHeartbeat = false
                confirmationPending = true
                val reply = LlmAssistantReply(
                    text = result.prompt,
                    emotion = null,
                    imageRequired = false,
                )
                deliverAssistantReply(reply)
            }
        }
    }

    private fun processConfirmationResponse(confirmed: Boolean, phrase: String) {
        confirmationPending = false
        val turnId = ++llmTurnGeneration
        llmJob?.cancel()
        emotionTransitionJob?.cancel()
        HotwordController.beginAssistantTurn()
        lastUserPhraseForTopic = phrase

        val state = _uiState.value
        conversationLogBeforeCurrentTurn = state.conversationLog
        _uiState.update {
            it.copy(
                phase = ConversationPhase.Thinking,
                emotion = RobotEmotion.THINKING,
                statusMessage = messages.thinkingStatus(),
                currentUtterance = "",
                conversationLog = appendUserLine(state.conversationLog, phrase),
            )
        }

        llmJob = viewModelScope.launch {
            try {
                if (turnId != llmTurnGeneration) return@launch
                refreshReasoningEngineIfBodySettingsChanged()

                val result = reasoningEngine.continueAfterConfirmation(
                    confirmed = confirmed,
                    onIntermediateResponse = { intermediate ->
                        if (turnId != llmTurnGeneration) return@continueAfterConfirmation
                        if (!_uiState.value.isHotwordListeningActive) return@continueAfterConfirmation
                        handleIntermediateResponse(intermediate)
                    },
                )

                if (turnId != llmTurnGeneration) return@launch
                if (!_uiState.value.isHotwordListeningActive) return@launch

                handleReasoningResult(result)
            } catch (e: CancellationException) {
                if (turnId == llmTurnGeneration) {
                    recoverFromCancelledLlmTurn()
                }
                throw e
            } catch (e: Exception) {
                if (turnId == llmTurnGeneration) {
                    handleLlmFailure(LlmHttpErrors.formatForLog(e))
                }
            }
        }
    }

    private suspend fun shouldSuppressHeartbeat(result: ReasoningResult.Success): Boolean {
        if (!currentInputIsHeartbeat) return false

        val confidence = result.speakConfidence ?: return false
        if (result.finalText.isBlank()) return true

        val settings = heartbeatSettingsRepository.load()
        return confidence < settings.proactiveThreshold
    }

    /**
     * Finalizes the assistant turn after a tool chain that already streamed its text
     * via [handleIntermediateResponse]. Resumes STT listening without additional TTS.
     */
    private fun finalizeTurnWithoutSpeech(emotion: RobotEmotion?) {
        Log.i(TAG, "finalizeTurnWithoutSpeech: resuming STT")
        if (!_uiState.value.isHotwordListeningActive) {
            Log.w(TAG, "finalizeTurnWithoutSpeech: hotword not active, skip")
            return
        }

        conversationLogBeforeCurrentTurn = null
        lastLlmEmotion = emotion ?: lastLlmEmotion
        moodManager.setEphemeralExpression(lastLlmEmotion)
        resumeListeningAfterAssistantTurn()
    }

    /**
     * Job LLM annullato senza sostituto: evita STT bloccato in pausa e fase Thinking senza chiamata rete.
     */
    private fun recoverFromCancelledLlmTurn() {
        if (!_uiState.value.isHotwordListeningActive) return
        val phase = _uiState.value.phase
        if (
            phase !is ConversationPhase.Thinking &&
            phase !is ConversationPhase.CapturingImage
        ) {
            return
        }

        clearVisionPipeline()
        revertConversationLogIfTurnAborted()
        HotwordController.endAssistantTurn(postTtsCooldownMs, lastAssistantResponse)
        val listeningPhase = listeningPhaseAfterAssistantTurn()
        val status = if (listeningPhase is ConversationPhase.ActiveListening) {
            messages.activeListeningStatus(exitPhrase)
        } else {
            standbyStatusFor(_uiState.value.isNightMode)
        }
        _uiState.update {
            it.copy(
                phase = listeningPhase,
                statusMessage = status,
                currentUtterance = "",
                emotion = deriveDisplayEmotion(
                    mood = moodManager.currentMood.value,
                    phase = listeningPhase,
                    isNightMode = it.isNightMode,
                ),
            )
        }
        HotwordController.clearPendingPhrase()
        drainQueuedUtterance()
        drainDeferredInputs()
    }

    private fun clearVisionPipeline() {
        visionPipelineActive = false
        pendingVisionUserPhrase = null
    }

    private suspend fun deliverAssistantReply(
        reply: LlmAssistantReply,
        fallbackEmotion: RobotEmotion? = null,
    ) {
        val spokenText = reply.text.trim()
        if (spokenText.isBlank()) {
            handleLlmFailure(messages.emptyReplyError())
            return
        }

        lastAssistantResponse = spokenText
        lastLlmEmotion = reply.emotion ?: fallbackEmotion
        moodManager.setEphemeralExpression(lastLlmEmotion)
        conversationLogBeforeCurrentTurn = null

        viewModelScope.launch {
            userAwarenessRepository.update { current ->
                UserStateTracker.analyzeRobotResponse(spokenText, current)
            }
        }

        _uiState.update {
            it.copy(
                conversationLog = appendRobotLine(it.conversationLog, spokenText),
            )
        }
        refreshUiEmotionFromMood()

        speakResponse(text = spokenText, llmEmotion = lastLlmEmotion)
    }

    private fun handleVisionCaptureFailure(error: Throwable?) {
        clearVisionPipeline()
        HotwordController.endAssistantTurn(postTtsCooldownMs)
        val detail = error?.message.orEmpty()
        val message = if (error is SecurityException) {
            messages.cameraPermissionRequired()
        } else {
            messages.cameraCaptureFailed(detail)
        }
        showRecoverableAnger(message)
    }

    private suspend fun speakResponse(text: String, llmEmotion: RobotEmotion?) {
        if (!_uiState.value.isHotwordListeningActive) return

        ttsInterruptHandled = false
        val state = _uiState.value
        val speakingEmotion = deriveDisplayEmotion(
            mood = moodManager.currentMood.value,
            phase = ConversationPhase.Speaking,
            isNightMode = state.isNightMode,
        )
        val speakingIntensity = deriveDisplayIntensity(ConversationPhase.Speaking)
        _uiState.update {
            it.copy(
                phase = ConversationPhase.Speaking,
                emotion = speakingEmotion,
                emotionIntensity = speakingIntensity,
                statusMessage = messages.speakingStatus(exitPhrase),
            )
        }

        val textForTts = MarkdownStripper.strip(text)

        ttsRepository.speak(textForTts).fold(
            onSuccess = {
                if (!ttsInterruptHandled) {
                    resumeListeningAfterAssistantTurn()
                }
            },
            onFailure = { error ->
                if (error is TtsInterruptedException || ttsInterruptHandled) return@fold

                ensureAssistantTurnEnded()
                showRecoverableAnger(messages.ttsFailed(error.message.orEmpty()))
                drainQueuedUtterance()
                drainDeferredInputs()
            },
        )
    }

    private fun onSpeechInterrupted(transcript: String) {
        if (!_uiState.value.isHotwordListeningActive) return
        if (visionPipelineActive) return
        if (_uiState.value.phase !is ConversationPhase.Speaking) return
        if (EchoSpeechFilter.isLikelyAssistantEcho(transcript, LlmRepositoryImpl.DEFAULT_IMAGE_ACK)) return

        ttsInterruptHandled = true
        ttsRepository.stop()
        HotwordController.endAssistantTurn(cooldownMs = 0)

        _uiState.update {
            it.copy(
                phase = ConversationPhase.ActiveListening,
                emotion = RobotEmotion.SURPRISED,
                statusMessage = messages.activeListeningStatus(exitPhrase),
                currentUtterance = transcript.trim(),
            )
        }

        scheduleEmotionTransition(
            delayMs = INTERRUPT_SURPRISED_MS,
            targetEmotion = moodManager.currentMood.value.baseEmotion,
        ) { phase -> phase is ConversationPhase.ActiveListening }
    }

    private fun resumeListeningAfterAssistantTurn(emotionOverride: RobotEmotion? = null) {
        if (!_uiState.value.isHotwordListeningActive) return

        HotwordController.endAssistantTurn(postTtsCooldownMs, lastAssistantResponse)
        HotwordController.clearPendingPhrase()

        val listeningPhase = listeningPhaseAfterAssistantTurn()
        val emotion = emotionOverride ?: deriveDisplayEmotion(
            mood = moodManager.currentMood.value,
            phase = listeningPhase,
            isNightMode = _uiState.value.isNightMode,
        )
        val status = if (listeningPhase is ConversationPhase.ActiveListening) {
            messages.activeListeningStatus(exitPhrase)
        } else {
            standbyStatusFor(_uiState.value.isNightMode)
        }

        val intensity = deriveDisplayIntensity(listeningPhase)
        _uiState.update {
            it.copy(
                phase = listeningPhase,
                emotion = emotion,
                emotionIntensity = intensity,
                statusMessage = status,
                currentUtterance = "",
            )
        }
        drainQueuedUtterance()
        drainDeferredInputs()
    }

    private fun listeningPhaseAfterAssistantTurn(): ConversationPhase =
        if (voiceSessionActive) ConversationPhase.ActiveListening else ConversationPhase.WaitingForHotword

    private fun ensureAssistantTurnEnded() {
        if (!isAssistantTurnInProgress()) return
        Log.d(TAG, "ensureAssistantTurnEnded: releasing STT pause")
        HotwordController.endAssistantTurn(cooldownMs = 0)
        HotwordController.clearPendingPhrase()
    }

    private fun revertConversationLogIfTurnAborted() {
        val previous = conversationLogBeforeCurrentTurn ?: return
        conversationLogBeforeCurrentTurn = null
        _uiState.update { it.copy(conversationLog = previous) }
    }

    private fun handleLlmFailure(message: String) {
        if (!_uiState.value.isHotwordListeningActive) return
        Log.w(TAG, "handleLlmFailure: $message")

        conversationLogBeforeCurrentTurn = null
        HotwordController.endAssistantTurn(postTtsCooldownMs, lastAssistantResponse)
        HotwordController.clearPendingPhrase()
        _uiState.update {
            it.copy(
                phase = listeningPhaseAfterAssistantTurn(),
                currentUtterance = "",
            )
        }
        showRecoverableAnger(messages.llmFailed(message))
    }

    private fun onSessionEnded(reason: SessionEndReason) {
        if (!_uiState.value.isHotwordListeningActive) return
        val pendingQueued = queuedUtteranceForLlm?.trim().orEmpty()
        val pendingTranscript = _uiState.value.currentUtterance.trim()
        val fallbackPhrase = if (pendingQueued.isNotEmpty()) pendingQueued else pendingTranscript
        val shouldDispatchFallback =
            reason == SessionEndReason.SILENCE_TIMEOUT &&
                fallbackPhrase.isNotEmpty()

        if (shouldDispatchFallback) {
            Log.i(TAG, "onSessionEnded: dispatching fallback utterance before standby")
            voiceSessionActive = false
            queuedUtteranceForLlm = null
            sendPhraseToLlm(fallbackPhrase)
            return
        }

        voiceSessionActive = false
        viewModelScope.launch {
            val stored = robotContextRepository.getStoredState()
            if (RobotContextPolicy.isSessionScoped(stored)) {
                robotContextRepository.clearToNormal()
                Log.i(TAG, "Session ended — cleared session-scoped robot context")
            }
        }
        emotionTransitionJob?.cancel()
        llmTurnGeneration++
        llmJob?.cancel()
        queuedUtteranceForLlm = null
        revertConversationLogIfTurnAborted()
        clearVisionPipeline()
        ttsInterruptHandled = true
        ttsRepository.stop()
        HotwordController.endAssistantTurn(cooldownMs = 0)
        lastAssistantResponse = null
        lastLlmEmotion = null

        val message = when (reason) {
            SessionEndReason.EXIT_PHRASE -> messages.sessionEndedByExitPhrase(exitPhrase)
            SessionEndReason.SILENCE_TIMEOUT -> messages.sessionEndedBySilence()
        }

        val night = isNightModeNow()
        _uiState.update {
            it.copy(
                phase = ConversationPhase.WaitingForHotword,
                emotion = standbyEmotionFor(night),
                statusMessage = message,
                isNightMode = night,
                currentUtterance = "",
            )
        }
        memoryExtractionScheduler.requestRunOnce()
        activityLogExtractionScheduler.requestRunOnce()
    }

    private fun isAssistantTurnInProgress(): Boolean {
        val phase = _uiState.value.phase
        return phase is ConversationPhase.Thinking ||
            phase is ConversationPhase.CapturingImage ||
            phase is ConversationPhase.Speaking
    }

    private fun isNightModeNow(): Boolean = NightModeHelper.isNightMode(nightModeConfig)

    private fun applyMoodTriggerForUserPhrase(phrase: String) {
        when (UserInteractionToneDetector.detect(phrase)) {
            UserInteractionTone.APOLOGY -> moodManager.recordApology()
            UserInteractionTone.POSITIVE -> moodManager.recordPositiveInteraction()
            UserInteractionTone.NEGATIVE -> moodManager.recordNegativeInteraction()
            UserInteractionTone.NEUTRAL -> moodManager.touchLastInteraction()
        }
    }

    private fun deriveDisplayEmotion(
        mood: RobotMood,
        phase: ConversationPhase,
        isNightMode: Boolean,
    ): RobotEmotion = DisplayEmotionResolver.resolve(
        wellbeing = mood,
        ephemeral = moodManager.ephemeralExpression.value,
        phase = phase,
        isNightMode = isNightMode,
    )

    private fun deriveDisplayIntensity(phase: ConversationPhase): Float =
        DisplayEmotionResolver.resolveIntensity(
            wellbeing = moodManager.currentMood.value,
            ephemeral = moodManager.ephemeralExpression.value,
            phase = phase,
        )

    private fun refreshUiEmotionFromMood() {
        val state = _uiState.value
        if (!state.isHotwordListeningActive) return
        val phase = state.phase
        if (phase is ConversationPhase.Thinking || phase is ConversationPhase.CapturingImage) return

        val mood = moodManager.currentMood.value
        val emotion = deriveDisplayEmotion(
            mood = mood,
            phase = phase,
            isNightMode = state.isNightMode,
        )
        val intensity = deriveDisplayIntensity(phase)
        val moodUi = MoodUiStateMapper.from(
            mood = mood,
            ephemeral = moodManager.ephemeralExpression.value,
            displayEmotion = emotion,
            displayIntensity = intensity,
            idleMinutes = moodManager.getIdleMinutes(),
        )
        if (
            state.emotion != emotion ||
            state.emotionIntensity != intensity ||
            state.moodUiState != moodUi
        ) {
            _uiState.update {
                it.copy(
                    emotion = emotion,
                    emotionIntensity = intensity,
                    moodUiState = moodUi,
                )
            }
        }
    }

    private fun standbyEmotionFor(night: Boolean): RobotEmotion =
        deriveDisplayEmotion(
            mood = moodManager.currentMood.value,
            phase = ConversationPhase.WaitingForHotword,
            isNightMode = night,
        )

    private fun standbyStatusFor(night: Boolean): String =
        if (night) messages.waitingForHotwordNight(wakePhrase) else messages.waitingForHotword(wakePhrase)

    private fun startNightModeMonitor() {
        stopNightModeMonitor()
        nightModeMonitorJob = viewModelScope.launch {
            while (isActive) {
                delay(NIGHT_MODE_RECHECK_MS)
                syncNightModeWithStandby()
            }
        }
    }

    private fun stopNightModeMonitor() {
        nightModeMonitorJob?.cancel()
        nightModeMonitorJob = null
    }

    private fun startMoodMonitor() {
        stopMoodMonitor()
        viewModelScope.launch { moodManager.initialize() }
        moodMonitorJob = viewModelScope.launch {
            var previousMood = moodManager.currentMood.value
            combine(
                moodManager.currentMood,
                moodManager.ephemeralExpression,
            ) { mood, ephemeral -> mood to ephemeral }.collect { (current, _) ->
                if (current != previousMood) {
                    bodyExpressionController.onMoodTransition(
                        previous = previousMood,
                        current = current,
                        context = buildBodyExpressionContext(),
                    )
                    previousMood = current
                }
                refreshUiEmotionFromMood()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(MOOD_RECHECK_MS)
                moodManager.checkIdleTransition()
                moodManager.checkDecay()
                refreshUiEmotionFromMood()
                workingMemoryRepository.checkAndResetIfNewDay()
                userAwarenessRepository.checkAndResetIfNewDay()
                checkProactiveSpeakTimeout()
                checkAndTriggerReflection()
            }
        }
    }

    private fun stopMoodMonitor() {
        moodMonitorJob?.cancel()
        moodMonitorJob = null
        bodyExpressionController.cancel()
    }

    private fun buildBodyExpressionContext(): BodyExpressionContext {
        val state = _uiState.value
        return BodyExpressionContext(
            isStandby = state.isHotwordListeningActive && state.phase is ConversationPhase.WaitingForHotword,
            isLlmBusy = llmJob?.isActive == true,
            isVisionBusy = visionPipelineActive,
        )
    }

    /**
     * Check if user responded to the last proactive speak within 5 minutes.
     * If so, record it as a positive response.
     */
    private suspend fun checkProactiveSpeakResponse() {
        val lastSpeak = lastProactiveSpeakTime ?: return
        val elapsed = System.currentTimeMillis() - lastSpeak
        val withinWindow = elapsed < PROACTIVE_RESPONSE_WINDOW_MS

        if (withinWindow) {
            weeklyStatsRepository.recordPositiveResponse(lastProactiveTopic)
            lastProactiveSpeakTime = null
            lastProactiveTopic = null
        }
    }

    /**
     * Check if the proactive speak response window has expired without response.
     * If so, record it as ignored.
     */
    private suspend fun checkProactiveSpeakTimeout() {
        val lastSpeak = lastProactiveSpeakTime ?: return
        val elapsed = System.currentTimeMillis() - lastSpeak

        if (elapsed >= PROACTIVE_RESPONSE_WINDOW_MS) {
            weeklyStatsRepository.recordIgnoredSuggestion(lastProactiveTopic)
            lastProactiveSpeakTime = null
            lastProactiveTopic = null
        }
    }

    /**
     * Mark a proactive speak for response tracking.
     */
    private fun markProactiveSpeakForTracking(topic: String?) {
        lastProactiveSpeakTime = System.currentTimeMillis()
        lastProactiveTopic = topic
    }

    private fun recordTopicFromUserTurn() {
        val phrase = lastUserPhraseForTopic?.trim().orEmpty()
        lastUserPhraseForTopic = null
        if (phrase.isBlank()) return
        val topic = ConversationTopicExtractor.extract(phrase) ?: return
        viewModelScope.launch {
            val current = workingMemoryRepository.load()
            if (!current.hasDiscussedTopic(topic)) {
                workingMemoryRepository.recordTopic(topic)
            }
        }
    }

    /**
     * Extract a topic keyword from proactive speak text.
     * Simple heuristic: first noun-like word after common prefixes.
     */
    private fun extractTopicFromText(text: String): String? {
        val lower = text.lowercase()
        val keywords = listOf(
            "meteo", "tempo", "pioggia", "sole",
            "promemoria", "reminder", "ricorda",
            "cane", "gatto", "animale",
            "pranzo", "cena", "colazione",
            "riunione", "meeting", "call",
            "email", "messaggio", "notifica",
        )
        return keywords.firstOrNull { lower.contains(it) }
    }

    /**
     * Check if weekly reflection is due and trigger it.
     */
    private suspend fun checkAndTriggerReflection() {
        if (!weeklyStatsRepository.isReflectionDue()) return

        val stats = weeklyStatsRepository.load()
        val reflection = RobotInput.WeeklyReflection(
            totalInteractions = stats.totalInteractions,
            totalProactiveSpeaks = stats.totalProactiveSpeaks,
            positiveResponses = stats.positiveResponses,
            ignoredSuggestions = stats.ignoredSuggestions,
            usefulTopics = stats.topUsefulTopics(),
            ignoredTopics = stats.topIgnoredTopics(),
            successRatePercent = (stats.successRate() * 100).toInt(),
        )

        val envelope = SystemInputEnvelope.from(reflection)
        Log.i(TAG, "Triggering weekly reflection")
        SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
        weeklyStatsRepository.markReflectionDone()
    }

    private fun syncNightModeWithStandby() {
        if (!_uiState.value.isHotwordListeningActive) return

        val night = isNightModeNow()
        val state = _uiState.value
        if (night == state.isNightMode) return

        moodManager.onTrigger(if (night) MoodTrigger.NightMode else MoodTrigger.DayMode)

        if (state.phase is ConversationPhase.WaitingForHotword) {
            _uiState.update {
                it.copy(
                    isNightMode = night,
                    statusMessage = standbyStatusFor(night),
                )
            }
            refreshUiEmotionFromMood()
        } else {
            _uiState.update { it.copy(isNightMode = night) }
        }
    }

    private fun showRecoverableAnger(statusMessage: String) {
        ensureAssistantTurnEnded()
        emotionTransitionJob?.cancel()
        _uiState.update {
            it.copy(
                phase = listeningPhaseAfterAssistantTurn(),
                emotion = RobotEmotion.ANGRY,
                statusMessage = statusMessage,
            )
        }
        scheduleEmotionTransition(
            delayMs = ANGRY_RECOVERY_MS,
            targetEmotion = moodManager.currentMood.value.baseEmotion,
        ) { phase ->
            phase is ConversationPhase.ActiveListening ||
                phase is ConversationPhase.WaitingForHotword
        }
    }

    private fun scheduleEmotionTransition(
        delayMs: Long,
        targetEmotion: RobotEmotion,
        onlyIfPhase: (ConversationPhase) -> Boolean = { true },
    ) {
        emotionTransitionJob?.cancel()
        emotionTransitionJob = viewModelScope.launch {
            delay(delayMs)
            if (!_uiState.value.isHotwordListeningActive) return@launch
            if (!onlyIfPhase(_uiState.value.phase)) return@launch
            val intensity = moodManager.currentMood.value.intensity
            _uiState.update {
                it.copy(emotion = targetEmotion, emotionIntensity = intensity)
            }
        }
    }

    private fun appendUserLine(log: String, phrase: String): String {
        val line = messages.userLine(phrase)
        return if (log.isBlank()) line else "$log\n\n$line"
    }

    private fun appendRobotLine(log: String, response: String): String {
        val line = messages.robotLine(response)
        return if (log.isBlank()) line else "$log\n\n$line"
    }

    private fun appendSystemLine(log: String, source: String, text: String): String {
        val line = messages.systemLine(source, text)
        return if (log.isBlank()) line else "$log\n\n$line"
    }

    private fun onSystemInputReceived(envelope: SystemInputEnvelope) {
        val uiState = _uiState.value
        if (!InputPolicyEngine.canAcceptInput(uiState)) {
            Log.d(TAG, "Mic not active, dropping system input")
            return
        }
        if (envelope.input is RobotInput.Notification || envelope.input is RobotInput.Heartbeat) {
            val stored = kotlinx.coroutines.runBlocking { robotContextRepository.getStoredState() }
            if (RobotContextPolicy.shouldDropNotifications(stored)) {
                val kind = if (envelope.input is RobotInput.Heartbeat) "heartbeat" else "notification"
                Log.d(TAG, "DROP system $kind (robot context silent)")
                return
            }
        }
        if (envelope.input !is RobotInput.ScheduledTaskFired &&
            InputPolicyEngine.shouldSuppressForNightMode(uiState, envelope.input.priority)
        ) {
            Log.d(TAG, "Night mode, suppressing system input")
            return
        }
        if (InputPolicyEngine.canProcessNow(envelope.input.priority, uiState)) {
            deferredInputQueue.markSeen(envelope.dedupKey)
            sendSystemInputToLlm(envelope)
        } else {
            Log.i(TAG, "Deferring system input: ${envelope.input.sourceId}")
            deferredInputQueue.enqueue(envelope)
        }
    }

    private fun sendSystemInputToLlm(envelope: SystemInputEnvelope) {
        val turnId = ++llmTurnGeneration
        llmJob?.cancel()
        emotionTransitionJob?.cancel()
        HotwordController.beginAssistantTurn()

        val state = _uiState.value
        conversationLogBeforeCurrentTurn = state.conversationLog

        currentInputIsHeartbeat = envelope.input is RobotInput.Heartbeat
        val scheduledTaskId = (envelope.input as? RobotInput.ScheduledTaskFired)?.taskId
        scheduledTaskIdForFireAndCheckCompletion = if (scheduledTaskId != null &&
            kotlinx.coroutines.runBlocking {
                fireAndCheckRepository.shouldCompleteOnReminderHandled(scheduledTaskId)
            }
        ) {
            scheduledTaskId
        } else {
            null
        }

        val sourceLabel = when (val input = envelope.input) {
            is RobotInput.Notification -> input.appLabel
            is RobotInput.ScheduledTaskFired -> "Promemoria"
            is RobotInput.HardwareButton -> "Pulsante"
            is RobotInput.SensorReading -> input.sensorType
            is RobotInput.Heartbeat -> "Heartbeat"
            is RobotInput.WeeklyReflection -> "Riflessione"
        }
        val summaryText = when (val input = envelope.input) {
            is RobotInput.Notification -> input.text ?: input.title ?: "Nuova notifica"
            is RobotInput.ScheduledTaskFired -> input.message
            is RobotInput.HardwareButton -> input.action
            is RobotInput.SensorReading -> "${input.value} ${input.unit}"
            is RobotInput.Heartbeat -> "tick autonomo"
            is RobotInput.WeeklyReflection -> "auto-analisi settimanale"
        }

        _uiState.update {
            it.copy(
                phase = ConversationPhase.Thinking,
                emotion = RobotEmotion.THINKING,
                statusMessage = messages.thinkingStatus(),
                currentUtterance = "",
                conversationLog = appendSystemLine(state.conversationLog, sourceLabel, summaryText),
            )
        }

        llmJob = viewModelScope.launch {
            try {
                if (turnId != llmTurnGeneration) return@launch

                val result = reasoningEngine.processSystemInput(
                    envelope = envelope,
                    onIntermediateResponse = { intermediate ->
                        if (turnId != llmTurnGeneration) return@processSystemInput
                        if (!_uiState.value.isHotwordListeningActive) return@processSystemInput
                        handleIntermediateResponse(intermediate)
                    },
                )

                if (turnId != llmTurnGeneration) return@launch
                if (!_uiState.value.isHotwordListeningActive) return@launch

                handleReasoningResult(result)
            } catch (e: CancellationException) {
                if (turnId == llmTurnGeneration) {
                    recoverFromCancelledLlmTurn()
                }
                throw e
            }
        }
    }

    private fun drainDeferredInputs() {
        if (!_uiState.value.isHotwordListeningActive) return
        if (isAssistantTurnInProgress() || visionPipelineActive) return

        val deferred = deferredInputQueue.drain()
        if (deferred.isEmpty()) return

        Log.i(TAG, "Draining ${deferred.size} deferred system inputs")
        val first = deferred.first()
        sendSystemInputToLlm(first)
    }

    override fun onCleared() {
        llmJob?.cancel()
        emotionTransitionJob?.cancel()
        stopNightModeMonitor()
        stopMoodMonitor()
        ttsRepository.stop()
        if (_uiState.value.isHotwordListeningActive) {
            HotwordServiceStarter.stop(appContext)
        }
        memoryExtractionScheduler.stop()
        activityLogExtractionScheduler.stop()
        super.onCleared()
    }

    private fun runBlockingLoadSettings(): com.example.mydeskrobot.domain.llm.LlmSettings {
        return runBlocking {
            llmSettingsRepository.load()
        }
    }
}

data class ConversationMessages(
    val wakePhraseHint: (String) -> String,
    val exitPhraseHint: (String) -> String,
    val idleStatus: () -> String,
    val waitingForHotword: (String) -> String,
    val waitingForHotwordNight: (String) -> String,
    val activeListeningStatus: (String) -> String,
    val thinkingStatus: () -> String,
    val speakingStatus: (String) -> String,
    val userLine: (String) -> String,
    val robotLine: (String) -> String,
    val systemLine: (String, String) -> String,
    val sessionEndedByExitPhrase: (String) -> String,
    val sessionEndedBySilence: () -> String,
    val sttUnavailable: () -> String,
    val llmNotConfigured: () -> String,
    val llmFailed: (String) -> String,
    val ttsFailed: (String) -> String,
    val hotwordEngineStopped: () -> String,
    val capturingImageStatus: () -> String,
    val openingBrowserStatus: () -> String,
    val openingSpotifyStatus: () -> String,
    val openingDialerStatus: () -> String,
    val settingRobotContextStatus: () -> String,
    val webSearchStatus: () -> String,
    val fetchUrlStatus: () -> String,
    val analyzingImageStatus: () -> String,
    val cameraPermissionRequired: () -> String,
    val cameraCaptureFailed: (String) -> String,
    val emptyReplyError: () -> String,
    val visionLoopDetected: () -> String,
)
