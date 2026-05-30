package com.example.mydeskrobot.data.speech

/**
 * Pluggable STT backend (Android [android.speech.SpeechRecognizer] or Vosk).
 *
 * ## Contract for [listenWithChunks]
 *
 * - One call = one **listen segment** (mic open until provider detects end-of-segment silence).
 * - **[ChunkListener]**: optional streaming updates for UI.
 *   - `isFinal = false`: partial hypothesis (may change); must **not** be treated as committed text.
 *   - `isFinal = true`: final chunk for this segment (optional; segment may still end via return value).
 * - **Return value**: best transcript for the segment (often same as last final). Empty failure = no speech.
 *
 * Session policy (buffer, end-of-utterance, LLM) lives in [com.example.mydeskrobot.data.hotword.SttListeningOrchestrator],
 * not in implementations.
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
