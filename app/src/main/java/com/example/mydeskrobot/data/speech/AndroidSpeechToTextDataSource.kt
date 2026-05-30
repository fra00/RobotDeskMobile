package com.example.mydeskrobot.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * STT data source that reuses a single SpeechRecognizer instance to avoid
 * the system "beep" sound that occurs on every create/destroy cycle.
 */
class AndroidSpeechToTextDataSource(
    private val context: Context,
    private val languageTag: String = Locale.ITALIAN.toLanguageTag(),
    private val segmentSilenceMs: Long = 1_000L,
) : SpeechToTextDataSource {

    companion object {
        private const val TAG = "SttDataSource"
        private const val MINIMUM_LISTEN_WINDOW_MS = 15_000L
    }

    private val lock = Any()

    @Volatile
    private var persistentRecognizer: SpeechRecognizer? = null

    @Volatile
    private var isListening = false

    @Volatile
    private var listenStartAtMs: Long = 0L

    @Volatile
    private var restartCounter: Long = 0L

    override fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Gets or creates the shared SpeechRecognizer instance.
     * Must be called on Main thread.
     */
    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        synchronized(lock) {
            persistentRecognizer?.let { return it }

            val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            if (newRecognizer != null) {
                Log.d(TAG, "Created new SpeechRecognizer instance")
                persistentRecognizer = newRecognizer
            }
            return newRecognizer
        }
    }

    /**
     * Cancels any active listening session without destroying the recognizer.
     * Call this before TTS to stop capturing audio.
     */
    override fun cancelActiveListening() {
        synchronized(lock) {
            if (isListening) {
                try {
                    persistentRecognizer?.cancel()
                    Log.d(TAG, "Cancelled active listening")
                } catch (e: Exception) {
                    Log.w(TAG, "Error cancelling recognizer", e)
                }
                isListening = false
                listenStartAtMs = 0L
            }
        }
    }

    /**
     * Releases the SpeechRecognizer instance completely.
     * Call this when the listening service is stopped.
     */
    override fun release() {
        synchronized(lock) {
            persistentRecognizer?.let { recognizer ->
                try {
                    recognizer.cancel()
                } catch (_: Exception) {
                    // ignore
                }
                try {
                    recognizer.destroy()
                    Log.d(TAG, "Destroyed SpeechRecognizer instance")
                } catch (_: Exception) {
                    // ignore
                }
            }
            persistentRecognizer = null
            isListening = false
            listenStartAtMs = 0L
        }
    }

    /**
     * Forces recreation of the recognizer on next use.
     * Use after certain errors that corrupt the recognizer state.
     */
    private fun invalidateRecognizer() {
        synchronized(lock) {
            persistentRecognizer?.let { recognizer ->
                try {
                    recognizer.destroy()
                } catch (_: Exception) {
                    // ignore
                }
            }
            persistentRecognizer = null
            isListening = false
            listenStartAtMs = 0L
            Log.d(TAG, "Invalidated SpeechRecognizer (will recreate on next use)")
        }
    }

    override suspend fun listenOnce(): Result<String> = listenWithChunks(listener = null)

    override suspend fun listenWithChunks(listener: SpeechToTextDataSource.ChunkListener?): Result<String> = withContext(Dispatchers.Main) {
        if (!isRecognitionAvailable()) {
            return@withContext Result.failure(
                IllegalStateException("Speech recognition is not available on this device"),
            )
        }

        suspendCancellableCoroutine { continuation ->
            val recognizer = getOrCreateRecognizer()
            if (recognizer == null) {
                continuation.resume(
                    Result.failure(IllegalStateException("Failed to create SpeechRecognizer")),
                )
                return@suspendCancellableCoroutine
            }

            synchronized(lock) {
                isListening = true
                listenStartAtMs = System.currentTimeMillis()
                restartCounter += 1
            }
            Log.d(TAG, "startListening count=$restartCounter")

            val recognitionListener = object : RecognitionListener {
                private var finished = false

                private fun finishOnce(shouldInvalidate: Boolean = false, block: () -> Unit) {
                    if (finished) return
                    finished = true
                    synchronized(lock) {
                        isListening = false
                        listenStartAtMs = 0L
                        if (shouldInvalidate) {
                            invalidateRecognizer()
                        }
                    }
                    block()
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    val elapsed = if (listenStartAtMs > 0L) {
                        System.currentTimeMillis() - listenStartAtMs
                    } else {
                        -1L
                    }
                    Log.d(TAG, "onError code=$error afterMs=$elapsed")
                    val shouldInvalidate = when (error) {
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                        -> true
                        else -> false
                    }

                    finishOnce(shouldInvalidate) {
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(
                                    SpeechRecognitionException(
                                        errorCode = error,
                                        message = speechErrorMessage(error),
                                    ),
                                ),
                            )
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    val elapsed = if (listenStartAtMs > 0L) {
                        System.currentTimeMillis() - listenStartAtMs
                    } else {
                        -1L
                    }
                    Log.d(TAG, "onResults textLen=${text.length} afterMs=$elapsed")
                    if (text.isNotEmpty()) {
                        listener?.onChunk(
                            SpeechToTextDataSource.RecognitionChunk(text = text, isFinal = true),
                        )
                    }
                    finishOnce {
                        if (!continuation.isActive) return@finishOnce
                        if (text.isNotEmpty()) {
                            continuation.resume(Result.success(text))
                        } else {
                            continuation.resume(
                                Result.failure(
                                    SpeechRecognitionException(
                                        errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                                        message = speechErrorMessage(SpeechRecognizer.ERROR_NO_MATCH),
                                    ),
                                ),
                            )
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isEmpty()) return
                    Log.v(TAG, "onPartial len=${partial.length}")
                    listener?.onChunk(SpeechToTextDataSource.RecognitionChunk(text = partial, isFinal = false))
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            }

            recognizer.setRecognitionListener(recognitionListener)
            recognizer.startListening(createRecognizerIntent(enablePartialResults = listener != null))

            continuation.invokeOnCancellation {
                synchronized(lock) {
                    isListening = false
                    listenStartAtMs = 0L
                    try {
                        recognizer.cancel()
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    private fun createRecognizerIntent(enablePartialResults: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, enablePartialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Ask the engine for longer sessions to reduce start/end churn
            // (and therefore reduce repeated system beeps).
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                MINIMUM_LISTEN_WINDOW_MS,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                segmentSilenceMs,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                segmentSilenceMs + 200L,
            )
        }

    private fun speechErrorMessage(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        else -> "Unknown speech error ($errorCode)"
    }
}
