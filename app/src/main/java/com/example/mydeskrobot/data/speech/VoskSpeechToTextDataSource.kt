package com.example.mydeskrobot.data.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * STT data source using Vosk for local, offline speech recognition.
 * No system beeps - full control over audio capture.
 *
 * Implements [SpeechToTextDataSource] for easy swap with Android recognizer.
 */
class VoskSpeechToTextDataSource(
    private val context: Context,
    private val modelManager: VoskModelManager,
) : SpeechToTextDataSource {

    companion object {
        private const val TAG = "VoskSttDataSource"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SILENCE_TIMEOUT_MS = 15_000L
        private const val BUFFER_SIZE_FACTOR = 2
        private const val END_OF_SPEECH_SILENCE_MS = 1_500L
    }

    private val lock = Any()

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recognizer: Recognizer? = null

    @Volatile
    private var isListening = false

    @Volatile
    private var listenStartAtMs: Long = 0L

    @Volatile
    private var restartCounter: Long = 0L

    override fun isRecognitionAvailable(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val modelReady = modelManager.isModelReady()

        Log.d(TAG, "isRecognitionAvailable: permission=$hasPermission, modelReady=$modelReady")
        return hasPermission && modelReady
    }

    fun isModelReady(): Boolean = modelManager.isModelReady()

    override fun cancelActiveListening() {
        synchronized(lock) {
            if (isListening) {
                Log.d(TAG, "Cancelling active listening")
                stopRecording()
                isListening = false
                listenStartAtMs = 0L
            }
        }
    }

    override fun release() {
        synchronized(lock) {
            Log.d(TAG, "Releasing resources")
            stopRecording()
            recognizer?.close()
            recognizer = null
            isListening = false
            listenStartAtMs = 0L
        }
    }

    private fun stopRecording() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    override suspend fun listenOnce(): Result<String> = listenWithChunks(listener = null)

    override suspend fun listenWithChunks(listener: SpeechToTextDataSource.ChunkListener?): Result<String> = withContext(Dispatchers.IO) {
        if (!isRecognitionAvailable()) {
            val reason = when {
                !modelManager.isModelReady() -> "Vosk model not downloaded"
                else -> "Microphone permission not granted"
            }
            return@withContext Result.failure(
                SpeechRecognitionException(
                    errorCode = -1,
                    message = reason,
                ),
            )
        }

        suspendCancellableCoroutine { continuation ->
            synchronized(lock) {
                isListening = true
                listenStartAtMs = System.currentTimeMillis()
                restartCounter += 1
            }
            Log.d(TAG, "startListening count=$restartCounter")

            val model: Model? = modelManager.getModelIfReady()
            if (model == null) {
                synchronized(lock) {
                    isListening = false
                    listenStartAtMs = 0L
                }
                continuation.resume(
                    Result.failure(
                        SpeechRecognitionException(
                            errorCode = -1,
                            message = "Failed to load Vosk model",
                        ),
                    ),
                )
                return@suspendCancellableCoroutine
            }

            val rec: Recognizer
            try {
                rec = Recognizer(model, SAMPLE_RATE.toFloat())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create recognizer", e)
                synchronized(lock) {
                    isListening = false
                    listenStartAtMs = 0L
                }
                continuation.resume(
                    Result.failure(
                        SpeechRecognitionException(
                            errorCode = -1,
                            message = "Failed to create Vosk recognizer: ${e.message}",
                        ),
                    ),
                )
                return@suspendCancellableCoroutine
            }

            synchronized(lock) {
                recognizer = rec
            }

            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

            val record: AudioRecord
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize,
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "No permission for AudioRecord", e)
                rec.close()
                synchronized(lock) {
                    recognizer = null
                    isListening = false
                    listenStartAtMs = 0L
                }
                continuation.resume(
                    Result.failure(
                        SpeechRecognitionException(
                            errorCode = -1,
                            message = "Microphone permission required",
                        ),
                    ),
                )
                return@suspendCancellableCoroutine
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                record.release()
                rec.close()
                synchronized(lock) {
                    recognizer = null
                    isListening = false
                    listenStartAtMs = 0L
                }
                continuation.resume(
                    Result.failure(
                        SpeechRecognitionException(
                            errorCode = -1,
                            message = "Failed to initialize audio recording",
                        ),
                    ),
                )
                return@suspendCancellableCoroutine
            }

            synchronized(lock) {
                audioRecord = record
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "Listening cancelled")
                synchronized(lock) {
                    stopRecording()
                    recognizer?.close()
                    recognizer = null
                    isListening = false
                    listenStartAtMs = 0L
                }
            }

            try {
                record.startRecording()
                Log.d(TAG, "AudioRecord started")

                val buffer = ShortArray(bufferSize / 2)
                var lastSpeechAt = System.currentTimeMillis()
                var lastPartialChangeAt = System.currentTimeMillis()
                var finalText: String? = null
                var lastPartialText = ""
                var hasSpeechStarted = false
                var shouldStop = false

                while (coroutineContext.isActive && continuation.isActive && !shouldStop) {
                    val stillListening = synchronized(lock) { isListening }
                    if (!stillListening) {
                        Log.d(TAG, "Listening stopped externally")
                        shouldStop = true
                        continue
                    }

                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) {
                        continue
                    }

                    val bytes = shortsToBytes(buffer, read)
                    val isFinal = rec.acceptWaveForm(bytes, bytes.size)

                    if (isFinal) {
                        val resultJson = rec.result
                        val text = parseVoskResult(resultJson)
                        if (text.isNotBlank()) {
                            Log.d(TAG, "Final result: $text")
                            listener?.onChunk(SpeechToTextDataSource.RecognitionChunk(text = text, isFinal = true))
                            finalText = text
                            lastSpeechAt = System.currentTimeMillis()
                            shouldStop = true
                            continue
                        }
                    } else {
                        val partialJson = rec.partialResult
                        val partialText = parseVoskPartial(partialJson)
                        if (partialText.isNotBlank() && partialText != lastPartialText) {
                            Log.v(TAG, "Partial: $partialText")
                            listener?.onChunk(SpeechToTextDataSource.RecognitionChunk(text = partialText, isFinal = false))
                            lastPartialText = partialText
                            lastPartialChangeAt = System.currentTimeMillis()
                            lastSpeechAt = System.currentTimeMillis()
                            hasSpeechStarted = true
                        }
                    }

                    val now = System.currentTimeMillis()

                    if (hasSpeechStarted && 
                        lastPartialText.isNotBlank() && 
                        now - lastPartialChangeAt > END_OF_SPEECH_SILENCE_MS
                    ) {
                        Log.d(TAG, "End of speech detected (no new partials for ${END_OF_SPEECH_SILENCE_MS}ms)")
                        val lastResult = rec.finalResult
                        val text = parseVoskResult(lastResult).ifBlank { lastPartialText }
                        if (text.isNotBlank()) {
                            Log.d(TAG, "Final result on speech end: $text")
                            listener?.onChunk(SpeechToTextDataSource.RecognitionChunk(text = text, isFinal = true))
                            finalText = text
                        }
                        shouldStop = true
                        continue
                    }

                    if (now - lastSpeechAt > SILENCE_TIMEOUT_MS) {
                        Log.d(TAG, "Silence timeout reached")
                        val lastResult = rec.finalResult
                        val text = parseVoskResult(lastResult)
                        if (text.isNotBlank()) {
                            Log.d(TAG, "Final result on timeout: $text")
                            listener?.onChunk(SpeechToTextDataSource.RecognitionChunk(text = text, isFinal = true))
                            finalText = text
                        }
                        shouldStop = true
                    }
                }

                val elapsed = System.currentTimeMillis() - listenStartAtMs
                Log.d(TAG, "Listening ended afterMs=$elapsed finalText=${finalText?.length ?: 0}")

                synchronized(lock) {
                    stopRecording()
                    recognizer?.close()
                    recognizer = null
                    isListening = false
                    listenStartAtMs = 0L
                }

                if (continuation.isActive) {
                    if (!finalText.isNullOrBlank()) {
                        continuation.resume(Result.success(finalText))
                    } else {
                        continuation.resume(
                            Result.failure(
                                SpeechRecognitionException(
                                    errorCode = 7,
                                    message = "No speech recognized",
                                ),
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during listening", e)
                synchronized(lock) {
                    stopRecording()
                    recognizer?.close()
                    recognizer = null
                    isListening = false
                    listenStartAtMs = 0L
                }
                if (continuation.isActive) {
                    continuation.resume(
                        Result.failure(
                            SpeechRecognitionException(
                                errorCode = -1,
                                message = "Recording error: ${e.message}",
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun shortsToBytes(shorts: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    private fun parseVoskResult(json: String): String {
        return try {
            JSONObject(json).optString("text", "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseVoskPartial(json: String): String {
        return try {
            JSONObject(json).optString("partial", "").trim()
        } catch (e: Exception) {
            ""
        }
    }
}
