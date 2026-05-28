package com.example.mydeskrobot.presentation.conversation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mydeskrobot.BuildConfig
import com.example.mydeskrobot.R
import com.example.mydeskrobot.data.llm.LlmModule
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.data.llm.LlmSettingsRepositoryImpl
import com.example.mydeskrobot.data.speech.AndroidSpeechToTextDataSource
import com.example.mydeskrobot.data.speech.AndroidTextToSpeechDataSource
import com.example.mydeskrobot.data.speech.SpeechToTextRepositoryImpl
import com.example.mydeskrobot.data.speech.TextToSpeechRepositoryImpl
import com.example.mydeskrobot.domain.model.BoredIdleConfig
import com.example.mydeskrobot.domain.time.NightModeConfig
import com.example.mydeskrobot.domain.speech.WakePhraseMatcher
import com.example.mydeskrobot.domain.vision.VisionImageCapture
import com.example.mydeskrobot.integration.ReasoningModule
import kotlinx.coroutines.runBlocking

class ConversationViewModelFactory(
    private val context: Context,
    private val visionImageCapture: VisionImageCapture,
) : ViewModelProvider.Factory {

    private val ttsDataSource = AndroidTextToSpeechDataSource(context.applicationContext)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        val appContext = context.applicationContext
        val wakePhrase = context.getString(R.string.wake_phrase)
        val exitPhrase = context.getString(R.string.out_phrase)
        val systemPrompt = LlmPromptLoader.loadSystemPrompt(appContext)
        val wakePhraseMatcher = WakePhraseMatcher(wakePhrase = wakePhrase)
        val sttDataSource = AndroidSpeechToTextDataSource(appContext)
        val speechRepository = SpeechToTextRepositoryImpl(
            dataSource = sttDataSource,
            wakePhraseMatcher = wakePhraseMatcher,
        )
        val llmRepository = LlmModule.createRepository(systemPrompt = systemPrompt)
        val ttsRepository = TextToSpeechRepositoryImpl(dataSource = ttsDataSource)

        val messages = ConversationMessages(
            wakePhraseHint = { phrase ->
                context.getString(R.string.wake_phrase_hint, phrase)
            },
            exitPhraseHint = { phrase ->
                val pauseSec = context.getString(R.string.utterance_pause_seconds)
                context.getString(R.string.out_phrase_hint, phrase, pauseSec)
            },
            idleStatus = {
                context.getString(R.string.status_idle)
            },
            waitingForHotword = { phrase ->
                context.getString(R.string.status_waiting_hotword, phrase)
            },
            waitingForHotwordNight = { phrase ->
                context.getString(R.string.status_waiting_hotword_night, phrase)
            },
            activeListeningStatus = { exit ->
                context.getString(R.string.status_active_listening, exit)
            },
            thinkingStatus = {
                context.getString(R.string.status_thinking)
            },
            speakingStatus = { exit ->
                context.getString(R.string.status_speaking, exit)
            },
            userLine = { phrase ->
                context.getString(R.string.line_user, phrase)
            },
            robotLine = { response ->
                context.getString(R.string.line_robot, response)
            },
            sessionEndedByExitPhrase = { exit ->
                context.getString(R.string.status_session_ended_exit, exit)
            },
            sessionEndedBySilence = {
                context.getString(R.string.status_session_ended_silence)
            },
            sttUnavailable = {
                context.getString(R.string.error_stt_unavailable)
            },
            llmNotConfigured = {
                context.getString(R.string.error_llm_not_configured_settings)
            },
            llmFailed = { detail ->
                context.getString(R.string.error_llm_failed, detail)
            },
            ttsFailed = { detail ->
                context.getString(R.string.error_tts_failed, detail)
            },
            hotwordEngineStopped = {
                context.getString(R.string.error_hotword_engine_stopped)
            },
            capturingImageStatus = {
                context.getString(R.string.status_capturing_image)
            },
            analyzingImageStatus = {
                context.getString(R.string.status_analyzing_image)
            },
            cameraPermissionRequired = {
                context.getString(R.string.error_camera_permission)
            },
            cameraCaptureFailed = { detail ->
                context.getString(R.string.error_camera_capture, detail)
            },
            emptyReplyError = {
                context.getString(R.string.error_llm_empty_reply)
            },
            visionLoopDetected = {
                context.getString(R.string.error_vision_loop)
            },
        )

        val postTtsCooldownMs = context.getString(R.string.post_tts_cooldown_seconds)
            .toLongOrNull()
            ?.coerceIn(0L, 5L)
            ?.times(1000L)
            ?: 1000L

        val boredIdleConfig = BoredIdleConfig(
            idleBeforeBoredMs = secondsFromString(
                context.getString(R.string.bored_idle_before_seconds),
                defaultSeconds = 60L,
                minSeconds = 15L,
                maxSeconds = 600L,
            ),
            boredDurationMs = secondsFromString(
                context.getString(R.string.bored_expression_seconds),
                defaultSeconds = 4L,
                minSeconds = 2L,
                maxSeconds = 15L,
            ),
            repeatIntervalMs = secondsFromString(
                context.getString(R.string.bored_repeat_interval_seconds),
                defaultSeconds = 90L,
                minSeconds = 30L,
                maxSeconds = 600L,
            ),
        )

        val nightModeConfig = NightModeConfig(
            startHour = hourFromString(
                context.getString(R.string.night_mode_start_hour),
                defaultHour = 0,
            ),
            endHour = hourFromString(
                context.getString(R.string.night_mode_end_hour),
                defaultHour = 6,
            ),
        )

        val llmSettingsRepository = LlmSettingsRepositoryImpl.create(appContext)
        val initialLlmSettings = runBlocking { llmSettingsRepository.load() }
        val reasoningEngine = ReasoningModule.createReasoningEngine(
            context = appContext,
            visionImageCapture = visionImageCapture,
            llmSettings = initialLlmSettings,
        )

        return ConversationViewModel(
            appContext = appContext,
            speechRepository = speechRepository,
            llmRepository = llmRepository,
            ttsRepository = ttsRepository,
            wakePhrase = wakePhrase,
            exitPhrase = exitPhrase,
            postTtsCooldownMs = postTtsCooldownMs,
            boredIdleConfig = boredIdleConfig,
            nightModeConfig = nightModeConfig,
            visionImageCapture = visionImageCapture,
            messages = messages,
            reasoningEngine = reasoningEngine,
            llmSettingsRepository = llmSettingsRepository,
        ) as T
    }

    private fun hourFromString(raw: String, defaultHour: Int): Int =
        raw.toIntOrNull()?.coerceIn(0, 23) ?: defaultHour

    private fun secondsFromString(
        raw: String,
        defaultSeconds: Long,
        minSeconds: Long,
        maxSeconds: Long,
    ): Long =
        raw.toLongOrNull()
            ?.coerceIn(minSeconds, maxSeconds)
            ?.times(1000L)
            ?: (defaultSeconds * 1000L)
}
