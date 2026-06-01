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
import com.example.mydeskrobot.domain.llm.LlmEmotionMapper
import com.example.mydeskrobot.domain.llm.LlmSettings
import com.example.mydeskrobot.domain.llm.LlmSettingsRepository
import com.example.mydeskrobot.integration.ReasoningModule
import com.example.mydeskrobot.integration.llm.LlmClientFactory
import com.example.mydeskrobot.memory.MemorySettingsRepository
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.extract.MemoryExtractionScheduler
import com.example.mydeskrobot.memory.extract.MemoryExtractionService
import com.example.mydeskrobot.presentation.settings.HeartbeatSettingsFormState
import com.example.mydeskrobot.presentation.settings.LlmSettingsFormState
import com.example.mydeskrobot.presentation.settings.MemorySettingsFormState
import com.example.mydeskrobot.presentation.settings.SettingsUiState
import com.example.mydeskrobot.presentation.settings.toDomain
import com.example.mydeskrobot.presentation.settings.toFormState
import com.example.mydeskrobot.reasoning.ReasoningEngine
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import com.example.mydeskrobot.reasoning.model.IntermediateResponse
import com.example.mydeskrobot.reasoning.model.ReasoningResult
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope
import com.example.mydeskrobot.integration.input.DeferredInputQueue
import com.example.mydeskrobot.integration.input.InputPolicyEngine
import com.example.mydeskrobot.data.context.RobotContextRepository
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettingsRepository
import com.example.mydeskrobot.data.input.InputSettingsRepository
import com.example.mydeskrobot.data.mood.MoodRepository
import com.example.mydeskrobot.data.workingmemory.WorkingMemoryRepository
import com.example.mydeskrobot.data.awareness.UserAwarenessRepository
import com.example.mydeskrobot.data.reflection.WeeklyStatsRepository
import com.example.mydeskrobot.domain.awareness.UserStateTracker
import com.example.mydeskrobot.domain.context.RobotContextPolicy
import com.example.mydeskrobot.domain.mood.MoodManager
import com.example.mydeskrobot.domain.mood.MoodTrigger
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
    private var boredIdleJob: Job? = null
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
    /** After a system input in standby, open voice session when the robot finishes speaking. */
    private var openVoiceSessionAfterSystemInput = false
    private val memoryRepository = UserMemoryRepository.create(appContext)
    private val memorySettingsRepository = MemorySettingsRepository(appContext)
    private val voskModelManager = VoskModelManager(appContext)
    private val sttSettingsRepository = SttSettingsRepository(appContext)
    private val deferredInputQueue = DeferredInputQueue()
    private val inputSettingsRepository = InputSettingsRepository(appContext)
    private val robotContextRepository = RobotContextRepository(appContext)
    private val heartbeatSettingsRepository = HeartbeatSettingsRepository(appContext)
    /** True when the current LLM turn was triggered by a heartbeat input. */
    private var currentInputIsHeartbeat = false
    private val moodRepository = MoodRepository(appContext)
    private val moodManager = MoodManager(moodRepository, scope = viewModelScope)
    private var moodMonitorJob: Job? = null
    private val workingMemoryRepository = WorkingMemoryRepository(appContext)
    private val weeklyStatsRepository = WeeklyStatsRepository(appContext)
    private val userAwarenessRepository = UserAwarenessRepository(appContext)
    /** Timestamp of last proactive speak, for tracking response. */
    private var lastProactiveSpeakTime: Long? = null
    /** Topic of last proactive speak, for attribution. */
    private var lastProactiveTopic: String? = null
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

    companion object {
        private const val TAG = "ConversationVM"
        private const val SURPRISED_FLASH_MS = 450L
        private const val HAPPY_AFTER_WAKE_MS = 1_800L
        private const val INTERRUPT_SURPRISED_MS = 280L
        private const val ANGRY_RECOVERY_MS = 2_500L
        private const val BORED_RECHECK_MS = 5_000L
        private const val NIGHT_MODE_RECHECK_MS = 60_000L
        private const val MOOD_RECHECK_MS = 30_000L
        /** Time window to consider user response as "positive" to proactive speak. */
        private const val PROACTIVE_RESPONSE_WINDOW_MS = 5 * 60_000L
    }

    init {
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
    }

    fun onEvent(event: ConversationUiEvent) {
        when (event) {
            ConversationUiEvent.OnToggleHotwordListening -> toggleHotwordListening()
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
            val preview = memoryRepository.getAllActive().map { it.value }.take(8)
            _settingsUiState.update {
                it.copy(
                    showMainDialog = false,
                    showMemoryDialog = true,
                    memoryForm = settings.toFormState(),
                    memoryListPreview = preview,
                    feedbackMessage = null,
                )
            }
        }
    }

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
            memoryRepository.resetMemory()
            memorySettingsRepository.setLastProcessedEntryCount(0L)
            _settingsUiState.update {
                it.copy(
                    memoryListPreview = emptyList(),
                    feedbackMessage = appContext.getString(R.string.memory_reset_done),
                    feedbackIsError = false,
                )
            }
        }
    }

    private fun reorganizeMemoryManual() {
        viewModelScope.launch {
            val removed = memoryRepository.reorganize()
            val preview = memoryRepository.getAllActive().map { it.value }.take(8)
            _settingsUiState.update {
                it.copy(
                    memoryListPreview = preview,
                    feedbackMessage = if (removed > 0) {
                        "Memoria riorganizzata: $removed duplicati rimossi"
                    } else {
                        "Memoria già ottimizzata"
                    },
                    feedbackIsError = false,
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
                    appContext.getString(R.string.llm_test_failed, error.message.orEmpty())
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
        reasoningEngine.reset()
        reasoningEngine = ReasoningModule.createReasoningEngine(
            context = appContext,
            visionImageCapture = visionImageCapture,
            llmSettings = settings,
        )
        return true
    }

    private fun toggleHotwordListening() {
        if (_uiState.value.isHotwordListeningActive) {
            disableHotwordListening()
        } else {
            enableHotwordListening()
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
        openVoiceSessionAfterSystemInput = false
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
        startBoredIdleMonitor()
        startNightModeMonitor()
        startMoodMonitor()
        memoryExtractionScheduler.start()
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
        queuedUtteranceForLlm = null
        voiceSessionActive = false
        openVoiceSessionAfterSystemInput = false
        clearVisionPipeline()
        stopBoredIdleMonitor()
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
            )
        }
        memoryExtractionScheduler.stop()
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
        openVoiceSessionAfterSystemInput = false

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

            val moodAfterSurprise = if (night) RobotEmotion.DROWSY else RobotEmotion.HAPPY
            _uiState.update { it.copy(emotion = moodAfterSurprise) }

            delay(HAPPY_AFTER_WAKE_MS)
            if (!_uiState.value.isHotwordListeningActive) return@launch
            if (_uiState.value.phase !is ConversationPhase.ActiveListening) return@launch
            if (_uiState.value.emotion == moodAfterSurprise) {
                _uiState.update { it.copy(emotion = RobotEmotion.LISTENING) }
            }
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
                emotion = RobotEmotion.LISTENING,
                currentUtterance = text.trim(),
                statusMessage = messages.activeListeningStatus(exitPhrase),
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
                    val facts = memoryRepository.getAllActive().take(8)
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
                    memoryRepository.resetMemory()
                    memorySettingsRepository.setLastProcessedEntryCount(0L)
                    speakMemoryCommandReply(phrase, "Ho cancellato tutta la memoria personale.")
                }
                "forget" -> {
                    val text = phrase.substringAfter("dimentica", "").trim()
                    val deleted = if (text.isBlank()) 0 else memoryRepository.forgetByText(text)
                    val reply = if (deleted > 0) {
                        "Fatto, ho dimenticato: $text."
                    } else {
                        "Non ho trovato informazioni su \"$text\" da dimenticare."
                    }
                    speakMemoryCommandReply(phrase, reply)
                }
            }
        }
        return true
    }

    private suspend fun speakMemoryCommandReply(userPhrase: String, robotReply: String) {
        if (!_uiState.value.isHotwordListeningActive) return
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
        val turnId = ++llmTurnGeneration
        llmJob?.cancel()
        emotionTransitionJob?.cancel()
        HotwordController.beginAssistantTurn()

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
        moodManager.recordInteraction()

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
        HotwordController.beginBargeIn(text)
        try {
            ttsRepository.speak(textForTts).onFailure { error ->
                if (error !is TtsInterruptedException) {
                    // Don't switch to recoverable anger here: the chain will continue.
                }
            }
        } finally {
            // Re-pause STT for the upcoming tool execution / next LLM turn.
            // The terminal end will be done by speakResponse or finalizeTurnWithoutSpeech.
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
                val reply = LlmAssistantReply(
                    text = result.prompt,
                    emotion = null,
                    imageRequired = false,
                )
                deliverAssistantReply(reply)
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
        lastLlmEmotion?.let { moodManager.onLlmEmotion(it) }
        resumeListeningAfterAssistantTurn(emotionOverride = lastLlmEmotion)
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
        _uiState.update {
            it.copy(
                phase = ConversationPhase.ActiveListening,
                emotion = lastLlmEmotion ?: RobotEmotion.LISTENING,
                statusMessage = messages.activeListeningStatus(exitPhrase),
                currentUtterance = "",
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
        lastLlmEmotion?.let { moodManager.onLlmEmotion(it) }
        conversationLogBeforeCurrentTurn = null

        viewModelScope.launch {
            userAwarenessRepository.update { current ->
                UserStateTracker.analyzeRobotResponse(spokenText, current)
            }
        }

        _uiState.update {
            it.copy(
                conversationLog = appendRobotLine(it.conversationLog, spokenText),
                emotion = reply.emotion ?: fallbackEmotion ?: it.emotion,
            )
        }

        speakResponse(text = spokenText, llmEmotion = reply.emotion ?: fallbackEmotion)
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
        val speakingEmotion = llmEmotion ?: RobotEmotion.SPEAKING
        _uiState.update {
            it.copy(
                phase = ConversationPhase.Speaking,
                emotion = speakingEmotion,
                statusMessage = messages.speakingStatus(exitPhrase),
            )
        }

        val textForTts = MarkdownStripper.strip(text)
        HotwordController.beginBargeIn(text)

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
            targetEmotion = RobotEmotion.LISTENING,
        ) { phase -> phase is ConversationPhase.ActiveListening }
    }

    private fun resumeListeningAfterAssistantTurn(emotionOverride: RobotEmotion? = null) {
        if (!_uiState.value.isHotwordListeningActive) return

        HotwordController.endAssistantTurn(postTtsCooldownMs, lastAssistantResponse)
        HotwordController.clearPendingPhrase()

        val openingVoiceSession = openVoiceSessionAfterSystemInput
        if (openingVoiceSession) {
            openVoiceSessionAfterSystemInput = false
            Log.i(TAG, "resumeListening: opening voice session after system input (was standby)")
            HotwordController.activateVoiceSession()
        }

        val listeningPhase = if (voiceSessionActive || openingVoiceSession) {
            ConversationPhase.ActiveListening
        } else {
            ConversationPhase.WaitingForHotword
        }
        val emotion = emotionOverride ?: lastLlmEmotion ?: if (listeningPhase is ConversationPhase.ActiveListening) {
            RobotEmotion.LISTENING
        } else {
            standbyEmotionFor(_uiState.value.isNightMode)
        }
        val status = if (listeningPhase is ConversationPhase.ActiveListening) {
            messages.activeListeningStatus(exitPhrase)
        } else {
            standbyStatusFor(_uiState.value.isNightMode)
        }

        _uiState.update {
            it.copy(
                phase = listeningPhase,
                emotion = emotion,
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

        conversationLogBeforeCurrentTurn = null
        HotwordController.endAssistantTurn(postTtsCooldownMs, lastAssistantResponse)
        HotwordController.clearPendingPhrase()
        _uiState.update {
            it.copy(
                phase = ConversationPhase.ActiveListening,
                currentUtterance = "",
            )
        }
        showRecoverableAnger(messages.llmFailed(message))
    }

    private fun onSessionEnded(reason: SessionEndReason) {
        if (!_uiState.value.isHotwordListeningActive) return
        voiceSessionActive = false
        openVoiceSessionAfterSystemInput = false
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
    }

    private fun isAssistantTurnInProgress(): Boolean {
        val phase = _uiState.value.phase
        return phase is ConversationPhase.Thinking ||
            phase is ConversationPhase.CapturingImage ||
            phase is ConversationPhase.Speaking
    }

    private fun startBoredIdleMonitor() {
        stopBoredIdleMonitor()
        boredIdleJob = viewModelScope.launch {
            while (isActive) {
                delay(boredIdleConfig.idleBeforeBoredMs)
                if (!canShowBoredInStandby()) {
                    delay(BORED_RECHECK_MS)
                    continue
                }
                showBoredExpressionBriefly()
                delay(boredIdleConfig.repeatIntervalMs)
            }
        }
    }

    private fun stopBoredIdleMonitor() {
        boredIdleJob?.cancel()
        boredIdleJob = null
    }

    private fun canShowBoredInStandby(): Boolean {
        val state = _uiState.value
        return state.isHotwordListeningActive &&
            state.phase is ConversationPhase.WaitingForHotword &&
            !state.isNightMode
    }

    private suspend fun showBoredExpressionBriefly() {
        if (!canShowBoredInStandby()) return

        emotionTransitionJob?.cancel()
        _uiState.update { it.copy(emotion = RobotEmotion.BORED) }

        delay(boredIdleConfig.boredDurationMs)

        if (canShowBoredInStandby() && _uiState.value.emotion == RobotEmotion.BORED) {
            _uiState.update { it.copy(emotion = RobotEmotion.NEUTRAL) }
        }
    }

    private fun isNightModeNow(): Boolean = NightModeHelper.isNightMode(nightModeConfig)

    private fun standbyEmotionFor(night: Boolean): RobotEmotion =
        if (night) RobotEmotion.SLEEPING else RobotEmotion.NEUTRAL

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
            moodManager.currentMood.collect { mood ->
                syncMoodWithUi(mood.baseEmotion)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(MOOD_RECHECK_MS)
                moodManager.checkIdleTransition()
                moodManager.checkDecay()
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
    }

    private fun syncMoodWithUi(moodEmotion: RobotEmotion) {
        val state = _uiState.value
        if (!state.isHotwordListeningActive) return
        if (state.phase !is ConversationPhase.WaitingForHotword) return

        if (state.isNightMode) {
            if (moodEmotion != RobotEmotion.SLEEPING && moodEmotion != RobotEmotion.DROWSY) {
                return
            }
        }

        _uiState.update { it.copy(emotion = moodEmotion) }
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
                    emotion = standbyEmotionFor(night),
                    statusMessage = standbyStatusFor(night),
                )
            }
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
            targetEmotion = RobotEmotion.CONFUSED,
        ) { phase -> phase is ConversationPhase.ActiveListening }
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
            _uiState.update { it.copy(emotion = targetEmotion) }
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
        openVoiceSessionAfterSystemInput = !voiceSessionActive
        HotwordController.beginAssistantTurn()

        val state = _uiState.value
        conversationLogBeforeCurrentTurn = state.conversationLog

        currentInputIsHeartbeat = envelope.input is RobotInput.Heartbeat

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
        stopBoredIdleMonitor()
        stopNightModeMonitor()
        stopMoodMonitor()
        ttsRepository.stop()
        if (_uiState.value.isHotwordListeningActive) {
            HotwordServiceStarter.stop(appContext)
        }
        memoryExtractionScheduler.stop()
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
    val settingRobotContextStatus: () -> String,
    val webSearchStatus: () -> String,
    val fetchUrlStatus: () -> String,
    val analyzingImageStatus: () -> String,
    val cameraPermissionRequired: () -> String,
    val cameraCaptureFailed: (String) -> String,
    val emptyReplyError: () -> String,
    val visionLoopDetected: () -> String,
)
