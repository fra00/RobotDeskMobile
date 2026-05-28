package com.example.mydeskrobot.data.speech

/**
 * Common interface for STT data sources.
 * Allows swapping between Android SpeechRecognizer and Vosk.
 */
interface SpeechToTextDataSource {

    data class RecognitionChunk(
        val text: String,
        val isFinal: Boolean,
    )

    fun interface ChunkListener {
        fun onChunk(chunk: RecognitionChunk)
    }

    fun isRecognitionAvailable(): Boolean

    fun cancelActiveListening()

    fun release()

    suspend fun listenOnce(): Result<String>

    suspend fun listenWithChunks(listener: ChunkListener?): Result<String>
}
