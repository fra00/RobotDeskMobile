package com.example.mydeskrobot.data.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.mydeskrobot.domain.mood.TtsProsody
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

class AndroidTextToSpeechDataSource(
    context: Context,
) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val initDeferred = CompletableDeferred<Boolean>()
    private val activeSpeakContinuation =
        AtomicReference<CancellableContinuation<Result<Unit>>?>(null)

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag("it-IT")
            }
            initDeferred.complete(status == TextToSpeech.SUCCESS)
        }
    }

    suspend fun speak(
        text: String,
        prosody: TtsProsody = TtsProsody.NEUTRAL,
    ): Result<Unit> = withContext(Dispatchers.Main) {
        if (!initDeferred.await()) {
            return@withContext Result.failure(IllegalStateException("TextToSpeech init failed"))
        }

        val engine = tts ?: return@withContext Result.failure(
            IllegalStateException("TextToSpeech not available"),
        )

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty TTS text"))
        }

        // Mood-driven voice shaping; reset to defaults happens on the next call.
        engine.setPitch(prosody.pitch)
        engine.setSpeechRate(prosody.rate)

        suspendCancellableCoroutine { continuation ->
            activeSpeakContinuation.set(continuation)
            val utteranceId = "robot_response_${System.currentTimeMillis()}"

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    activeSpeakContinuation.compareAndSet(continuation, null)
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    activeSpeakContinuation.compareAndSet(continuation, null)
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(IllegalStateException("TTS playback error")),
                        )
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    activeSpeakContinuation.compareAndSet(continuation, null)
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(
                                IllegalStateException("TTS playback error ($errorCode)"),
                            ),
                        )
                    }
                }
            })

            val params = Bundle()
            val result = engine.speak(trimmed, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(IllegalStateException("TTS speak failed")))
                }
            }

            continuation.invokeOnCancellation {
                activeSpeakContinuation.compareAndSet(continuation, null)
                engine.stop()
            }
        }
    }

    fun stop() {
        tts?.stop()
        activeSpeakContinuation.getAndSet(null)?.let { continuation ->
            if (continuation.isActive) {
                continuation.resume(Result.failure(TtsInterruptedException()))
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
