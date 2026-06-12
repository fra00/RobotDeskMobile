package com.example.mydeskrobot.data.speech

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * Briefly lowers beep-related streams only while Android STT is actively listening.
 * Does not touch [AudioManager.STREAM_MUSIC] so TTS keeps normal volume.
 */
class SttBeepSuppressor private constructor(
    private val audioManager: AudioManager?,
) {

    constructor(context: Context) : this(
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager,
    )

    private val lock = Any()

    private var listenHoldCount = 0
    private val savedVolumes = mutableMapOf<Int, Int>()

    /** Call immediately before [SpeechRecognizer.startListening]. */
    fun onListenStarted(tag: String) {
        synchronized(lock) {
            listenHoldCount++
            if (listenHoldCount == 1) {
                applyListenMute()
            }
            Log.d(TAG, "onListenStarted tag=$tag holds=$listenHoldCount")
        }
    }

    /** Call when the listen segment ends (results, error, or cancel). */
    fun onListenEnded(tag: String) {
        synchronized(lock) {
            if (listenHoldCount <= 0) {
                Log.w(TAG, "onListenEnded ignored tag=$tag holds=0")
                return
            }
            listenHoldCount--
            Log.d(TAG, "onListenEnded tag=$tag holds=$listenHoldCount")
            if (listenHoldCount == 0) {
                restoreListenVolumes()
            }
        }
    }

    fun forceRestore(tag: String) {
        synchronized(lock) {
            if (listenHoldCount == 0) return
            Log.d(TAG, "forceRestore tag=$tag holds=$listenHoldCount")
            listenHoldCount = 0
            restoreListenVolumes()
        }
    }

    @VisibleForTesting
    internal fun holdCountForTesting(): Int = synchronized(lock) { listenHoldCount }

    private fun applyListenMute() {
        val manager = audioManager ?: return
        LISTEN_STREAMS.forEach { stream ->
            if (!savedVolumes.containsKey(stream)) {
                savedVolumes[stream] = manager.getStreamVolume(stream)
            }
            manager.setStreamVolume(stream, 0, 0)
        }
    }

    private fun restoreListenVolumes() {
        val manager = audioManager ?: run {
            savedVolumes.clear()
            return
        }
        savedVolumes.forEach { (stream, volume) ->
            manager.setStreamVolume(stream, volume, 0)
        }
        savedVolumes.clear()
    }

    companion object {
        private const val TAG = "SttBeepSuppressor"

        /** Beep streams only — never STREAM_MUSIC (used by TTS). */
        val LISTEN_STREAMS = intArrayOf(
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
        )

        @VisibleForTesting
        internal fun createForTest(audioManager: AudioManager?): SttBeepSuppressor =
            SttBeepSuppressor(audioManager)
    }
}
