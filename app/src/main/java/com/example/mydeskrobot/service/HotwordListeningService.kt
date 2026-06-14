package com.example.mydeskrobot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mydeskrobot.MainActivity
import com.example.mydeskrobot.R
import com.example.mydeskrobot.data.hotword.HotwordController
import com.example.mydeskrobot.data.hotword.ListeningConfig
import com.example.mydeskrobot.data.hotword.SttListeningOrchestrator
import com.example.mydeskrobot.data.power.DeviceStayAwakeManager
import com.example.mydeskrobot.data.speech.AndroidSpeechToTextDataSource
import com.example.mydeskrobot.data.speech.SpeechToTextDataSource
import com.example.mydeskrobot.data.speech.SttBeepSuppressor
import com.example.mydeskrobot.data.speech.SttSettingsRepository
import com.example.mydeskrobot.data.speech.VoskModelManager
import com.example.mydeskrobot.data.speech.VoskSpeechToTextDataSource
import com.example.mydeskrobot.domain.speech.SttProvider
import kotlinx.coroutines.runBlocking
import com.example.mydeskrobot.domain.hotword.HotwordEvent
import com.example.mydeskrobot.domain.hotword.HotwordEventDispatcher
import com.example.mydeskrobot.domain.speech.ExitPhraseMatcher
import com.example.mydeskrobot.domain.speech.WakePhraseMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HotwordListeningService : Service() {
    companion object {
        const val ACTION_START = "com.example.mydeskrobot.hotword.START"
        const val ACTION_STOP = "com.example.mydeskrobot.hotword.STOP"
        private const val TAG = "HotwordService"
        private const val CHANNEL_ID = "hotword_listening"
        private const val NOTIFICATION_ID = 1001
        private const val DEFAULT_UTTERANCE_PAUSE_SECONDS = 2L
        private const val MIN_UTTERANCE_PAUSE_SECONDS = 1L
        private const val MAX_UTTERANCE_PAUSE_SECONDS = 5L
        private const val BALANCED_END_OF_UTTERANCE_MS = 1_800L
        private const val DEFAULT_SILENCE_SECONDS = 15L
        private const val MIN_SILENCE_SECONDS = 5L
        private const val MAX_SILENCE_SECONDS = 120L
        private const val DEFAULT_POST_TTS_COOLDOWN_SECONDS = 1L
        private const val MIN_POST_TTS_COOLDOWN_SECONDS = 0L
        private const val MAX_POST_TTS_COOLDOWN_SECONDS = 5L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listenJob: Job? = null
    private var resumeJob: Job? = null
    @Volatile
    private var sttPaused = false

    @Volatile
    private var bargeInMode = false

    @Volatile
    private var bargeInEchoReference: String? = null

    /** True from [beginAssistantTurn] until [endAssistantTurn] finishes (incl. post-TTS cooldown). */
    @Volatile
    private var assistantTurnActive = false

    /** True while a phone call is active — blocks [endAssistantTurn] from reopening STT. */
    @Volatile
    private var phoneCallHoldActive = false

    private lateinit var stayAwakeManager: DeviceStayAwakeManager
    private lateinit var listeningConfig: ListeningConfig
    private lateinit var voskModelManager: VoskModelManager
    private lateinit var sttSettingsRepository: SttSettingsRepository
    private var speechDataSource: SpeechToTextDataSource? = null
    private var orchestrator: SttListeningOrchestrator? = null
    private var sttBeepSuppressor: SttBeepSuppressor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        HotwordController.register(this)
        stayAwakeManager = DeviceStayAwakeManager(applicationContext)
        listeningConfig = buildListeningConfig()
        voskModelManager = VoskModelManager(applicationContext)
        sttSettingsRepository = SttSettingsRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListeningLoop()
                stayAwakeManager.release()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                stayAwakeManager.acquire()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startListeningLoop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopListeningLoop()
        if (::stayAwakeManager.isInitialized) {
            stayAwakeManager.release()
        }
        if (::voskModelManager.isInitialized) {
            voskModelManager.release()
        }
        HotwordController.unregister(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    fun isDetecting(): Boolean = listenJob?.isActive == true

    fun getVoskModelManager(): VoskModelManager = voskModelManager

    fun isUsingVosk(): Boolean = speechDataSource is VoskSpeechToTextDataSource

    fun beginAssistantTurn() {
        Log.d(TAG, "beginAssistantTurn")
        resumeJob?.cancel()
        assistantTurnActive = true
        bargeInMode = false
        bargeInEchoReference = null
        sttPaused = true
        speechDataSource?.cancelActiveListening()
        orchestrator?.clearPendingPhrase()
        orchestrator?.resetSessionSilenceClock()
    }

    fun clearPendingPhrase() {
        orchestrator?.clearPendingPhrase()
    }

    /** Opens hotword voice session without saying the wake phrase (after notification TTS in standby). */
    fun activateVoiceSession() {
        Log.d(TAG, "activateVoiceSession")
        orchestrator?.requestActivateVoiceSession()
        speechDataSource?.cancelActiveListening()
    }

    fun beginBargeIn(lastAssistantResponse: String) {
        Log.d(TAG, "beginBargeIn")
        bargeInEchoReference = lastAssistantResponse
        bargeInMode = true
        sttPaused = false
        orchestrator?.resetSessionSilenceClock()
        speechDataSource?.cancelActiveListening()
    }

    fun beginPhoneCallHold() {
        Log.d(TAG, "beginPhoneCallHold")
        phoneCallHoldActive = true
        beginAssistantTurn()
    }

    fun endPhoneCallHold() {
        Log.d(TAG, "endPhoneCallHold")
        phoneCallHoldActive = false
        if (!assistantTurnActive) {
            sttPaused = false
            orchestrator?.resetSessionSilenceClock()
        }
    }

    fun endAssistantTurn(cooldownMs: Long, echoReferenceForCooldown: String? = null) {
        Log.d(TAG, "endAssistantTurn cooldownMs=$cooldownMs phoneCallHold=$phoneCallHoldActive")
        resumeJob?.cancel()
        resumeJob = serviceScope.launch {
            bargeInMode = false
            bargeInEchoReference = echoReferenceForCooldown
            if (phoneCallHoldActive) {
                assistantTurnActive = false
                return@launch
            }
            sttPaused = false
            orchestrator?.resetSessionSilenceClock()
            if (cooldownMs > 0) delay(cooldownMs)
            bargeInEchoReference = null
            assistantTurnActive = false
        }
    }

    private fun startListeningLoop() {
        sttPaused = false
        assistantTurnActive = false
        Log.d(TAG, "startListeningLoop")
        listenJob?.cancel()

        val provider = runBlocking { sttSettingsRepository.getProvider() }
        val useAndroidBeepSuppressor = provider == SttProvider.ANDROID ||
            (provider == SttProvider.VOSK && !voskModelManager.isModelReady())
        if (useAndroidBeepSuppressor) {
            sttBeepSuppressor = SttBeepSuppressor(applicationContext)
        }

        val dataSource = createSpeechDataSource(sttBeepSuppressor)
        speechDataSource = dataSource

        if (!dataSource.isRecognitionAvailable()) {
            Log.w(TAG, "Speech recognition not available")
            HotwordEventDispatcher.emit(HotwordEvent.EngineStopped)
            stopSelf()
            return
        }

        orchestrator = SttListeningOrchestrator(
            dataSource = dataSource,
            config = listeningConfig,
            wakePhraseMatcher = WakePhraseMatcher(wakePhrase = listeningConfig.wakePhrase),
            exitPhraseMatcher = ExitPhraseMatcher(exitPhrase = listeningConfig.exitPhrase),
            onEvent = { event -> HotwordEventDispatcher.emit(event) },
        )

        val activeOrchestrator = orchestrator!!
        listenJob = serviceScope.launch {
            activeOrchestrator.run(
                isServiceActive = { listenJob?.isActive == true },
                isSttEnabled = { !sttPaused && listenJob?.isActive == true },
                isAssistantTurnActive = { assistantTurnActive },
                isBargeInMode = { bargeInMode },
                bargeInEchoReference = { bargeInEchoReference },
            )
        }
    }

    private fun createSpeechDataSource(beepSuppressor: SttBeepSuppressor?): SpeechToTextDataSource {
        val provider = runBlocking { sttSettingsRepository.getProvider() }
        val segmentMs = listeningConfig.segmentSilenceMs

        return when (provider) {
            SttProvider.VOSK -> {
                if (voskModelManager.isModelReady()) {
                    Log.d(TAG, "Using Vosk STT (no system beeps) segmentSilenceMs=$segmentMs")
                    VoskSpeechToTextDataSource(
                        applicationContext,
                        voskModelManager,
                        segmentSilenceMs = segmentMs,
                    )
                } else {
                    Log.w(TAG, "Vosk selected but model not ready, falling back to Android STT")
                    AndroidSpeechToTextDataSource(
                        context = applicationContext,
                        beepSuppressor = beepSuppressor,
                        segmentSilenceMs = segmentMs,
                    )
                }
            }
            SttProvider.ANDROID -> {
                Log.d(TAG, "Using Android STT with beep suppressor segmentSilenceMs=$segmentMs")
                AndroidSpeechToTextDataSource(
                    context = applicationContext,
                    beepSuppressor = beepSuppressor,
                    segmentSilenceMs = segmentMs,
                )
            }
        }
    }

    private fun stopListeningLoop() {
        Log.d(TAG, "stopListeningLoop")
        resumeJob?.cancel()
        listenJob?.cancel()
        listenJob = null
        orchestrator = null
        sttPaused = false
        assistantTurnActive = false
        phoneCallHoldActive = false
        bargeInMode = false
        bargeInEchoReference = null
        speechDataSource?.release()
        speechDataSource = null
        sttBeepSuppressor?.forceRestore("stop")
        sttBeepSuppressor = null
    }

    private fun buildListeningConfig(): ListeningConfig {
        val utterancePauseSeconds = getString(R.string.utterance_pause_seconds)
            .toLongOrNull()
            ?.coerceIn(MIN_UTTERANCE_PAUSE_SECONDS, MAX_UTTERANCE_PAUSE_SECONDS)
            ?: DEFAULT_UTTERANCE_PAUSE_SECONDS

        val sessionSilenceSeconds = getString(R.string.conversation_silence_timeout_seconds)
            .toLongOrNull()
            ?.coerceIn(MIN_SILENCE_SECONDS, MAX_SILENCE_SECONDS)
            ?: DEFAULT_SILENCE_SECONDS

        val postTtsCooldownSeconds = getString(R.string.post_tts_cooldown_seconds)
            .toLongOrNull()
            ?.coerceIn(MIN_POST_TTS_COOLDOWN_SECONDS, MAX_POST_TTS_COOLDOWN_SECONDS)
            ?: DEFAULT_POST_TTS_COOLDOWN_SECONDS

        val endOfUtteranceMs = (utterancePauseSeconds * 1000L).let { configured ->
            if (configured == DEFAULT_UTTERANCE_PAUSE_SECONDS * 1000L) {
                BALANCED_END_OF_UTTERANCE_MS
            } else {
                configured
            }
        }

        return ListeningConfig(
            wakePhrase = getString(R.string.wake_phrase),
            exitPhrase = getString(R.string.out_phrase),
            endOfUtteranceMs = endOfUtteranceMs,
            segmentSilenceMs = ListeningConfig.segmentSilenceFor(endOfUtteranceMs),
            sessionSilenceTimeoutMs = sessionSilenceSeconds * 1000L,
            postTtsCooldownMs = postTtsCooldownSeconds * 1000L,
        )
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_hotword_title))
            .setContentText(
                getString(
                    R.string.notification_hotword_text,
                    listeningConfig.wakePhrase,
                ),
            )
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_hotword),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

}
