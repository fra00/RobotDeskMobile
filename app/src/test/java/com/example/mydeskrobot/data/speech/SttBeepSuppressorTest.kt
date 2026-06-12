package com.example.mydeskrobot.data.speech

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class SttBeepSuppressorTest {

    @Test
    fun onListenStarted_incrementsHoldCount() {
        val suppressor = SttBeepSuppressor.createForTest(audioManager = null)

        suppressor.onListenStarted("a")

        assertEquals(1, suppressor.holdCountForTesting())
    }

    @Test
    fun nestedListenSegments_requireMatchingEnds() {
        val suppressor = SttBeepSuppressor.createForTest(audioManager = null)

        suppressor.onListenStarted("first")
        suppressor.onListenStarted("second")

        assertEquals(2, suppressor.holdCountForTesting())

        suppressor.onListenEnded("second")
        assertEquals(1, suppressor.holdCountForTesting())

        suppressor.onListenEnded("first")
        assertEquals(0, suppressor.holdCountForTesting())
    }

    @Test
    fun onListenEndedWhenNotHeld_doesNotGoNegative() {
        val suppressor = SttBeepSuppressor.createForTest(audioManager = null)

        suppressor.onListenEnded("ghost")

        assertEquals(0, suppressor.holdCountForTesting())
    }

    @Test
    fun forceRestore_clearsActiveHold() {
        val suppressor = SttBeepSuppressor.createForTest(audioManager = null)

        suppressor.onListenStarted("segment")
        suppressor.forceRestore("stop")

        assertEquals(0, suppressor.holdCountForTesting())
    }

    @Test
    fun listenStreams_excludeMusic() {
        val streams = SttBeepSuppressor.LISTEN_STREAMS.toList()
        assertEquals(
            listOf(
                AudioManager.STREAM_SYSTEM,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_ALARM,
            ),
            streams,
        )
        assert(!streams.contains(AudioManager.STREAM_MUSIC))
    }
}
