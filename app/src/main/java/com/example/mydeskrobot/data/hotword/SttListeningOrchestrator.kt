package com.example.mydeskrobot.data.hotword

import android.speech.SpeechRecognizer
import android.util.Log
import com.example.mydeskrobot.data.speech.SpeechRecognitionException
import com.example.mydeskrobot.data.speech.SpeechToTextDataSource
import com.example.mydeskrobot.domain.hotword.HotwordEvent
import com.example.mydeskrobot.domain.hotword.SessionEndReason
import com.example.mydeskrobot.domain.speech.EchoSpeechFilter
import com.example.mydeskrobot.domain.speech.ExitPhraseMatcher
import com.example.mydeskrobot.domain.speech.ExitPhraseParseResult
import com.example.mydeskrobot.domain.speech.WakePhraseMatcher
import com.example.mydeskrobot.domain.speech.WakePhraseParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Standby: wake word only.
 * Active: buffers transcript segments; [ListeningConfig.endOfUtteranceMs] after last committed
 * content → [HotwordEvent.UtteranceReadyForLlm]; empty buffer + [sessionSilenceTimeoutMs] → standby.
 */
class SttListeningOrchestrator(
    private val dataSource: SpeechToTextDataSource,
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

    @Volatile
    private var activateVoiceSessionPending = false

    private var activePhraseBuffer: StringBuilder? = null

    /**
     * Opens a voice session without wake word (e.g. after announcing a notification in standby).
     * Cancels the current standby listen so the request is picked up on the next loop iteration.
     */
    fun requestActivateVoiceSession() {
        activateVoiceSessionPending = true
        resetSessionSilenceClock()
    }

    /** Resets session silence clock after TTS/LLM (paused time is not user silence). */
    fun resetSessionSilenceClock() {
        sessionSilenceClockMs = System.currentTimeMillis()
    }

    fun clearPendingPhrase() {
        activePhraseBuffer?.clear()
    }

    suspend fun run(
        isServiceActive: () -> Boolean,
        isSttEnabled: () -> Boolean,
        isAssistantTurnActive: () -> Boolean,
        isBargeInMode: () -> Boolean,
        bargeInEchoReference: () -> String?,
    ) {
        while (coroutineContext.isActive && isServiceActive()) {
            runStandbyCycle(
                isServiceActive = isServiceActive,
                isSttEnabled = isSttEnabled,
                isAssistantTurnActive = isAssistantTurnActive,
                isBargeInMode = isBargeInMode,
                bargeInEchoReference = bargeInEchoReference,
            )
        }
    }

    private suspend fun runStandbyCycle(
        isServiceActive: () -> Boolean,
        isSttEnabled: () -> Boolean,
        isAssistantTurnActive: () -> Boolean,
        isBargeInMode: () -> Boolean,
        bargeInEchoReference: () -> String?,
    ) {
        while (coroutineContext.isActive && isServiceActive()) {
            if (consumeActivateVoiceSessionRequest()) {
                Log.d(TAG, "Activating voice session after external prompt")
                enterActiveSession(
                    initialText = null,
                    isServiceActive = isServiceActive,
                    isSttEnabled = isSttEnabled,
                    isAssistantTurnActive = isAssistantTurnActive,
                    isBargeInMode = isBargeInMode,
                    bargeInEchoReference = bargeInEchoReference,
                )
                return
            }

            if (!isSttEnabled()) {
                delay(PAUSE_POLL_MS)
                continue
            }

            val standbyResult = dataSource.listenWithChunks(listener = null)
            val transcript = standbyResult.getOrNull()
            if (transcript != null) {
                if (!isSttEnabled()) continue

                when (val wake = wakePhraseMatcher.parse(transcript)) {
                    is WakePhraseParseResult.Accepted -> {
                            enterActiveSession(
                                initialText = wake.query,
                                isServiceActive = isServiceActive,
                                isSttEnabled = isSttEnabled,
                                isAssistantTurnActive = isAssistantTurnActive,
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
                                    isAssistantTurnActive = isAssistantTurnActive,
                                    isBargeInMode = isBargeInMode,
                                    bargeInEchoReference = bargeInEchoReference,
                                )
                            return
                        }

                        WakePhraseParseResult.RejectReason.MISSING_WAKE_PHRASE -> Unit
                    }
                }
            } else {
                val error = standbyResult.exceptionOrNull()
                if (error != null) {
                    handleBenignListenError(error)
                    val code = (error as? SpeechRecognitionException)?.errorCode
                    Log.d(TAG, "standby listen error=$code")
                }
            }
            delay(RESTART_DELAY_MS)
        }
    }

    private suspend fun enterActiveSession(
        initialText: String?,
        isServiceActive: () -> Boolean,
        isSttEnabled: () -> Boolean,
        isAssistantTurnActive: () -> Boolean,
        isBargeInMode: () -> Boolean,
        bargeInEchoReference: () -> String?,
    ) {
        onEvent(HotwordEvent.SessionStarted(initialText))

        val phraseBuffer = StringBuilder()
        activePhraseBuffer = phraseBuffer
        var lastContentAt = 0L

        if (!initialText.isNullOrBlank()) {
            val content = initialText.trim()
            if (!EchoSpeechFilter.isLikelyAssistantEcho(content, bargeInEchoReference())) {
                phraseBuffer.append(content)
                lastContentAt = System.currentTimeMillis()
                onEvent(HotwordEvent.UtteranceInProgress(phraseBuffer.toString()))
            }
        }

        sessionSilenceClockMs = System.currentTimeMillis()

        suspend fun tryFinalizePhrase(): Boolean {
            if (phraseBuffer.isBlank()) return false
            if (lastContentAt == 0L) return false
            if (System.currentTimeMillis() - lastContentAt < config.endOfUtteranceMs) return false

            val phrase = phraseBuffer.toString().trim()
            phraseBuffer.clear()
            if (phrase.isBlank()) return false
            if (EchoSpeechFilter.isLikelyAssistantEcho(phrase, bargeInEchoReference())) {
                Log.d(TAG, "finalize skipped (echo): '${phrase.take(40)}'")
                return false
            }

            Log.d(TAG, "UtteranceReadyForLlm: '${phrase.take(60)}'")
            onEvent(HotwordEvent.UtteranceReadyForLlm(phrase))
            lastContentAt = System.currentTimeMillis()
            sessionSilenceClockMs = lastContentAt
            return true
        }

        suspend fun shouldEndSessionForSilence(): Boolean {
            if (isAssistantTurnActive()) return false
            if (phraseBuffer.isNotBlank()) return false
            return System.currentTimeMillis() - sessionSilenceClockMs >= config.sessionSilenceTimeoutMs
        }

        val sessionScope = CoroutineScope(coroutineContext)
        val chunkListener = SpeechToTextDataSource.ChunkListener { chunk ->
            if (isBargeInMode()) return@ChunkListener
            if (chunk.isFinal) return@ChunkListener
            val partial = chunk.text.trim()
            if (partial.isNotEmpty()) {
                sessionScope.launch {
                    onEvent(HotwordEvent.UtteranceInProgress(partial))
                }
            }
        }

        while (coroutineContext.isActive && isServiceActive()) {
            if (!isSttEnabled()) {
                delay(PAUSE_POLL_MS)
                continue
            }

            if (!isBargeInMode() && tryFinalizePhrase()) {
                continue
            }

            if (shouldEndSessionForSilence()) {
                onEvent(HotwordEvent.SessionEnded(SessionEndReason.SILENCE_TIMEOUT))
                return
            }

            if (phraseBuffer.isEmpty()) {
                val activeResult = dataSource.listenWithChunks(listener = chunkListener)
                val transcript = activeResult.getOrNull()
                if (transcript != null) {
                    if (!isSttEnabled()) continue

                    if (isBargeInMode()) {
                        if (handleBargeInTranscript(
                                transcript = transcript,
                                echoReference = bargeInEchoReference(),
                            )
                        ) {
                            return
                        }
                    } else {
                        when (val exit = exitPhraseMatcher.parse(transcript)) {
                            ExitPhraseParseResult.ExitOnly -> {
                                tryFinalizePhrase()
                                onEvent(HotwordEvent.SessionEnded(SessionEndReason.EXIT_PHRASE))
                                return
                            }

                            is ExitPhraseParseResult.ContentThenExit -> {
                                if (appendUserTranscript(
                                        buffer = phraseBuffer,
                                        transcript = exit.content,
                                        echoReference = bargeInEchoReference,
                                    )
                                ) {
                                    lastContentAt = System.currentTimeMillis()
                                    sessionSilenceClockMs = lastContentAt
                                }
                                tryFinalizePhrase()
                                onEvent(HotwordEvent.SessionEnded(SessionEndReason.EXIT_PHRASE))
                                return
                            }

                            ExitPhraseParseResult.NotExit -> {
                                if (appendUserTranscript(
                                        buffer = phraseBuffer,
                                        transcript = transcript,
                                        echoReference = bargeInEchoReference,
                                    )
                                ) {
                                    lastContentAt = System.currentTimeMillis()
                                    sessionSilenceClockMs = lastContentAt
                                }
                                tryFinalizePhrase()
                            }
                        }
                    }
                } else {
                    val error = activeResult.exceptionOrNull()
                    if (error != null) {
                        val code = (error as? SpeechRecognitionException)?.errorCode
                        Log.d(TAG, "active listen error=$code bargeIn=${isBargeInMode()}")
                        when (code) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            -> if (!isBargeInMode()) tryFinalizePhrase()

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> delay(BUSY_RETRY_MS)

                            else -> delay(RESTART_DELAY_MS)
                        }

                        if (shouldEndSessionForSilence()) {
                            onEvent(HotwordEvent.SessionEnded(SessionEndReason.SILENCE_TIMEOUT))
                            return
                        }
                    }
                }
                delay(RESTART_DELAY_MS)
            } else {
                delay(PAUSE_POLL_MS)
            }
        }
    }

    private fun appendToPhrase(buffer: StringBuilder, chunk: String) {
        val trimmed = chunk.trim()
        if (trimmed.isEmpty()) return
        if (buffer.isNotEmpty()) buffer.append(' ')
        buffer.append(trimmed)
    }

    /** @return true if committed text was appended to the buffer. */
    private suspend fun appendUserTranscript(
        buffer: StringBuilder,
        transcript: String,
        echoReference: () -> String?,
    ): Boolean {
        val echoRef = echoReference()
        var content = stripWakePrefixIfPresent(transcript).trim()
        if (content.isBlank()) return false

        content = EchoSpeechFilter.stripLeadingAssistantEcho(content, echoRef)
        if (content.isBlank()) return false
        if (EchoSpeechFilter.isLikelyAssistantEcho(content, echoRef)) return false

        appendToPhrase(buffer, content)
        onEvent(HotwordEvent.UtteranceInProgress(buffer.toString()))
        return true
    }

    private fun stripWakePrefixIfPresent(transcript: String): String {
        return when (val wake = wakePhraseMatcher.parse(transcript)) {
            is WakePhraseParseResult.Accepted -> wake.query
            is WakePhraseParseResult.Rejected -> transcript.trim()
        }
    }

    /** @return true if the active session should end (e.g. exit phrase). */
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

    private fun consumeActivateVoiceSessionRequest(): Boolean {
        if (!activateVoiceSessionPending) return false
        activateVoiceSessionPending = false
        return true
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
