package com.example.mydeskrobot.data.speech

import android.speech.SpeechRecognizer
import java.util.ArrayDeque

/**
 * Test double for [SpeechToTextDataSource] with scripted listen segments.
 */
class FakeSpeechToTextDataSource : SpeechToTextDataSource {

    data class ListenScript(
        val transcript: String = "",
        val partials: List<String> = emptyList(),
        val latePartialsAfterFinal: List<String> = emptyList(),
        val error: SpeechRecognitionException? = null,
    )

    private val scripts = ArrayDeque<ListenScript>()

    @Volatile
    var listenCallCount: Int = 0
        private set

    fun enqueue(vararg script: ListenScript) {
        scripts.addAll(script)
    }

    override fun isRecognitionAvailable(): Boolean = true

    override fun cancelActiveListening() = Unit

    override fun release() = Unit

    override suspend fun listenOnce(): Result<String> = listenWithChunks(listener = null)

    override suspend fun listenWithChunks(listener: SpeechToTextDataSource.ChunkListener?): Result<String> {
        listenCallCount++
        val script = scripts.poll()
        if (script == null) {
            return Result.failure(
                SpeechRecognitionException(
                    errorCode = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    message = "No scripted listen",
                ),
            )
        }

        script.error?.let { return Result.failure(it) }

        script.partials.forEach { partial ->
            if (partial.isNotBlank()) {
                listener?.onChunk(
                    SpeechToTextDataSource.RecognitionChunk(text = partial, isFinal = false),
                )
            }
        }

        val text = script.transcript.trim()
        if (text.isNotEmpty()) {
            listener?.onChunk(SpeechToTextDataSource.RecognitionChunk(text = text, isFinal = true))
            script.latePartialsAfterFinal.forEach { partial ->
                if (partial.isNotBlank()) {
                    listener?.onChunk(
                        SpeechToTextDataSource.RecognitionChunk(text = partial, isFinal = false),
                    )
                }
            }
            return Result.success(text)
        }

        return Result.failure(
            SpeechRecognitionException(
                errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                message = "No speech recognized",
            ),
        )
    }
}
