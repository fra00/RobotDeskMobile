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
        private const val DEFAULT_UTTERANCE_PAUSE_SECONDS = 5L
        private const val MIN_UTTERANCE_PAUSE_SECONDS = 2L
        private const val MAX_UTTERANCE_PAUSE_SECONDS = 30L
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

    private lateinit var stayAwakeManager: DeviceStayAwakeManager
    private lateinit var listeningConfig: ListeningConfig
    private lateinit var speechDataSource: AndroidSpeechToTextDataSource
    private var orchestrator: SttListeningOrchestrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        HotwordController.register(this)
        stayAwakeManager = DeviceStayAwakeManager(applicationContext)
        listeningConfig = buildListeningConfig()
        speechDataSource = AndroidSpeechToTextDataSource(applicationContext)
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
        HotwordController.unregister(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    fun isDetecting(): Boolean = listenJob?.isActive == true

    fun beginAssistantTurn() {
        Log.d(TAG, "beginAssistantTurn")
        resumeJob?.cancel()
        bargeInMode = false
        bargeInEchoReference = null
        sttPaused = true
        speechDataSource.cancelActiveListening()
        orchestrator?.clearPendingPhrase()
    }

    fun clearPendingPhrase() {
        orchestrator?.clearPendingPhrase()
    }

    fun beginBargeIn(lastAssistantResponse: String) {
        Log.d(TAG, "beginBargeIn")
        bargeInEchoReference = lastAssistantResponse
        bargeInMode = true
        sttPaused = false
        orchestrator?.resetSessionSilenceClock()
        speechDataSource.cancelActiveListening()
    }

    fun endAssistantTurn(cooldownMs: Long, echoReferenceForCooldown: String? = null) {
        Log.d(TAG, "endAssistantTurn cooldownMs=$cooldownMs")
        resumeJob?.cancel()
        resumeJob = serviceScope.launch {
            bargeInMode = false
            bargeInEchoReference = echoReferenceForCooldown
            sttPaused = false
            orchestrator?.resetSessionSilenceClock()
            if (cooldownMs > 0) delay(cooldownMs)
            bargeInEchoReference = null
        }
    }

    private fun startListeningLoop() {
        if (!speechDataSource.isRecognitionAvailable()) {
            HotwordEventDispatcher.emit(HotwordEvent.EngineStopped)
            stopSelf()
            return
        }

        sttPaused = false
        Log.d(TAG, "startListeningLoop")
        listenJob?.cancel()
        orchestrator = SttListeningOrchestrator(
            dataSource = speechDataSource,
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
                isBargeInMode = { bargeInMode },
                bargeInEchoReference = { bargeInEchoReference },
            )
        }
    }

    private fun stopListeningLoop() {
        Log.d(TAG, "stopListeningLoop")
        resumeJob?.cancel()
        listenJob?.cancel()
        listenJob = null
        orchestrator = null
        sttPaused = false
        bargeInMode = false
        bargeInEchoReference = null
        speechDataSource.release()
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

        return ListeningConfig(
            wakePhrase = getString(R.string.wake_phrase),
            exitPhrase = getString(R.string.out_phrase),
            utterancePauseMs = utterancePauseSeconds * 1000L,
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
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_hotword),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

}
