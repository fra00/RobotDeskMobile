package com.example.mydeskrobot.data.hotword

import android.speech.SpeechRecognizer
import android.util.Log
import com.example.mydeskrobot.data.speech.AndroidSpeechToTextDataSource
import com.example.mydeskrobot.data.speech.SpeechRecognitionException
import com.example.mydeskrobot.domain.hotword.HotwordEvent
import com.example.mydeskrobot.domain.hotword.SessionEndReason
import com.example.mydeskrobot.domain.speech.EchoSpeechFilter
import com.example.mydeskrobot.domain.speech.ExitPhraseMatcher
import com.example.mydeskrobot.domain.speech.ExitPhraseParseResult
import com.example.mydeskrobot.domain.speech.WakePhraseMatcher
import com.example.mydeskrobot.domain.speech.WakePhraseParseResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Standby: solo wake word.
 * Attivo: trascrive tutto; pausa [utterancePauseMs] → LLM; silenzio [sessionSilenceTimeoutMs] senza
 * frase in corso → standby (serve di nuovo la wake word).
 */
class SttListeningOrchestrator(
    private val dataSource: AndroidSpeechToTextDataSource,
    private val config: ListeningConfig,
    private val wakePhraseMatcher: WakePhraseMatcher,
    private val exitPhraseMatcher: ExitPhraseMatcher,
    private val onEvent: suspend (HotwordEvent) -> Unit,
) {
    companion object {
        private const val TAG = "SttOrchestrator"
        private const val RESTART_DELAY_MS = 400L
        private const val PAUSE_POLL_MS = 200L
        private const val BUSY_RETRY_MS = 600L
        private const val MIN_BARGE_IN_CHARS = 4
    }

    @Volatile
    private var sessionSilenceClockMs: Long = 0L

    private var activePhraseBuffer: StringBuilder? = null

    /** Ripristina il timer di sessione dopo TTS/LLM (il tempo in pausa non conta come silenzio utente). */
    fun resetSessionSilenceClock() {
        sessionSilenceClockMs = System.currentTimeMillis()
    }

    fun clearPendingPhrase() {
        activePhraseBuffer?.clear()
    }

    suspend fun run(
        isServiceActive: () -> Boolean,
        isSttEnabled: () -> Boolean,
        isBargeInMode: () -> Boolean,
        bargeInEchoReference: () -> String?,
    ) {
        while (coroutineContext.isActive && isServiceActive()) {
            runStandbyCycle(
                isServiceActive = isServiceActive,
                isSttEnabled = isSttEnabled,
                isBargeInMode = isBargeInMode,
                bargeInEchoReference = bargeInEchoReference,
            )
        }
    }

    private suspend fun runStandbyCycle(
        isServiceActive: () -> Boolean,
        isSttEnabled: () -> Boolean,
        isBargeInMode: () -> Boolean,
        bargeInEchoReference: () -> String?,
    ) {
        while (coroutineContext.isActive && isServiceActive()) {
            if (!isSttEnabled()) {
                delay(PAUSE_POLL_MS)
                continue
            }

            dataSource.listenWithChunks(listener = null).fold(
                onSuccess = { transcript ->
                    if (!isSttEnabled()) return@fold

                    when (val wake = wakePhraseMatcher.parse(transcript)) {
                        is WakePhraseParseResult.Accepted -> {
                            enterActiveSession(
                                initialText = wake.query,
                                isServiceActive = isServiceActive,
                                isSttEnabled = isSttEnabled,
                                isBargeInMode = isBargeInMode,
                                bargeInEchoReference = bargeInEchoReference,
                            )
                            return
                        }

                        is WakePhraseParseResult.Rejected -> when (wake.reason) {
                            WakePhraseParseResult.RejectReason.EMPTY_QUERY_AFTER_WAKE_PHRASE -> {
                                enterActiveSession(
                                    initialText = null,
                                    isServiceActive = isServiceActive,
                                    isSttEnabled = isSttEnabled,
                                    isBargeInMode = isBargeInMode,
                                    bargeInEchoReference = bargeInEchoReference,
                                )
                                return
                            }

                            WakePhraseParseResult.RejectReason.MISSING_WAKE_PHRASE -> Unit
                        }
                    }
                },
                onFailure = { error ->
                    handleBenignListenError(error)
                    val code = (error as? SpeechRecognitionException)?.errorCode
                    Log.d(TAG, "standby listen error=$code")
                },
            )
            delay(RESTART_DELAY_MS)
        }
    }

    private suspend fun enterActiveSession(
        initialText: String?,
        isServiceActive: () -> Boolean,
        isSttEnabled: () -> Boolean,
        isBargeInMode: () -> Boolean,
        bargeInEchoReference: () -> String?,
    ) {
        onEvent(HotwordEvent.SessionStarted(initialText))

        val phraseBuffer = StringBuilder()
        activePhraseBuffer = phraseBuffer
        if (!initialText.isNullOrBlank()) {
            val content = initialText.trim()
            if (!EchoSpeechFilter.isLikelyAssistantEcho(content, bargeInEchoReference())) {
                phraseBuffer.append(content)
                onEvent(HotwordEvent.UtteranceInProgress(phraseBuffer.toString()))
            }
        }

        sessionSilenceClockMs = System.currentTimeMillis()
        var lastSpeechAt = sessionSilenceClockMs

        suspend fun finalizePhraseIfPaused(): Boolean {
            if (phraseBuffer.isBlank()) return false
            if (System.currentTimeMillis() - lastSpeechAt < config.utterancePauseMs) return false

            val phrase = phraseBuffer.toString().trim()
            phraseBuffer.clear()
            if (phrase.isBlank()) return false
            if (EchoSpeechFilter.isLikelyAssistantEcho(phrase, bargeInEchoReference())) return false

            onEvent(HotwordEvent.UtteranceReadyForLlm(phrase))
            lastSpeechAt = System.currentTimeMillis()
            sessionSilenceClockMs = lastSpeechAt
            return true
        }

        suspend fun shouldEndSessionForSilence(): Boolean {
            if (phraseBuffer.isNotBlank()) return false
            return System.currentTimeMillis() - sessionSilenceClockMs >= config.sessionSilenceTimeoutMs
        }

        while (coroutineContext.isActive && isServiceActive()) {
            if (!isSttEnabled()) {
                delay(PAUSE_POLL_MS)
                continue
            }

            if (!isBargeInMode()) {
                finalizePhraseIfPaused()
            }

            if (shouldEndSessionForSilence()) {
                onEvent(HotwordEvent.SessionEnded(SessionEndReason.SILENCE_TIMEOUT))
                return
            }

            dataSource.listenWithChunks(
                listener = AndroidSpeechToTextDataSource.ChunkListener { chunk ->
                    if (isBargeInMode()) return@ChunkListener
                    if (chunk.isFinal) return@ChunkListener
                    lastSpeechAt = System.currentTimeMillis()
                    sessionSilenceClockMs = lastSpeechAt
                },
            ).fold(
                onSuccess = { transcript ->
                    if (!isSttEnabled()) return@fold

                    if (isBargeInMode()) {
                        if (handleBargeInTranscript(
                                transcript = transcript,
                                echoReference = bargeInEchoReference(),
                            )
                        ) {
                            return
                        }
                        return@fold
                    }

                    lastSpeechAt = System.currentTimeMillis()
                    sessionSilenceClockMs = lastSpeechAt
                    when (val exit = exitPhraseMatcher.parse(transcript)) {
                        ExitPhraseParseResult.ExitOnly -> {
                            finalizePhraseIfPaused()
                            onEvent(HotwordEvent.SessionEnded(SessionEndReason.EXIT_PHRASE))
                            return
                        }

                        is ExitPhraseParseResult.ContentThenExit -> {
                            appendUserTranscript(phraseBuffer, exit.content, bargeInEchoReference)
                            finalizePhraseIfPaused()
                            onEvent(HotwordEvent.SessionEnded(SessionEndReason.EXIT_PHRASE))
                            return
                        }

                        ExitPhraseParseResult.NotExit -> {
                            appendUserTranscript(phraseBuffer, transcript, bargeInEchoReference)
                        }
                    }
                },
                onFailure = { error ->
                    val code = (error as? SpeechRecognitionException)?.errorCode
                    Log.d(TAG, "active listen error=$code bargeIn=${isBargeInMode()}")
                    when (code) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        -> if (!isBargeInMode()) finalizePhraseIfPaused()

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> delay(BUSY_RETRY_MS)

                        else -> delay(RESTART_DELAY_MS)
                    }

                    if (shouldEndSessionForSilence()) {
                        onEvent(HotwordEvent.SessionEnded(SessionEndReason.SILENCE_TIMEOUT))
                        return
                    }
                },
            )

            delay(RESTART_DELAY_MS)
        }
    }

    private fun appendToPhrase(buffer: StringBuilder, chunk: String) {
        val trimmed = chunk.trim()
        if (trimmed.isEmpty()) return
        if (buffer.isNotEmpty()) buffer.append(' ')
        buffer.append(trimmed)
    }

    private suspend fun appendUserTranscript(
        buffer: StringBuilder,
        transcript: String,
        echoReference: () -> String?,
    ) {
        val echoRef = echoReference()
        var content = stripWakePrefixIfPresent(transcript).trim()
        if (content.isBlank()) return

        content = EchoSpeechFilter.stripLeadingAssistantEcho(content, echoRef)
        if (content.isBlank()) return
        if (EchoSpeechFilter.isLikelyAssistantEcho(content, echoRef)) return

        appendToPhrase(buffer, content)
        onEvent(HotwordEvent.UtteranceInProgress(buffer.toString()))
    }

    private fun stripWakePrefixIfPresent(transcript: String): String {
        return when (val wake = wakePhraseMatcher.parse(transcript)) {
            is WakePhraseParseResult.Accepted -> wake.query
            is WakePhraseParseResult.Rejected -> transcript.trim()
        }
    }

    /** @return true se la sessione attiva deve terminare (es. frase di uscita). */
    private suspend fun handleBargeInTranscript(
        transcript: String,
        echoReference: String?,
    ): Boolean {
        if (EchoSpeechFilter.isLikelyAssistantEcho(transcript, echoReference)) return false

        return when (val exit = exitPhraseMatcher.parse(transcript)) {
            ExitPhraseParseResult.ExitOnly -> {
                onEvent(HotwordEvent.SessionEnded(SessionEndReason.EXIT_PHRASE))
                true
            }

            is ExitPhraseParseResult.ContentThenExit -> {
                onEvent(HotwordEvent.SessionEnded(SessionEndReason.EXIT_PHRASE))
                true
            }

            ExitPhraseParseResult.NotExit -> {
                val content = stripWakePrefixIfPresent(transcript).trim()
                if (content.length >= MIN_BARGE_IN_CHARS) {
                    onEvent(HotwordEvent.SpeechInterrupted(content))
                }
                false
            }
        }
    }

    private suspend fun handleBenignListenError(error: Throwable) {
        val code = (error as? SpeechRecognitionException)?.errorCode
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            -> Unit

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> delay(BUSY_RETRY_MS)

            else -> delay(RESTART_DELAY_MS)
        }
    }

}
