package com.example.mydeskrobot.data.presence

import com.example.mydeskrobot.domain.presence.FaceGazeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FaceGazeStateStoreTest {

    @Before
    fun resetStore() {
        FaceGazeStateStore.reset()
    }

    @Test
    fun `lastFaceSeen persists when gaze cleared to null`() {
        val t0 = 1_000_000L
        FaceGazeStateStore.update(
            FaceGazeSnapshot(
                horizontalOffset = 0.1f,
                verticalOffset = 0f,
                confidence = 0.8f,
                capturedAt = t0,
            ),
        )
        FaceGazeStateStore.update(null)

        assertNull(FaceGazeStateStore.current())
        assertEquals(t0, FaceGazeStateStore.lastFaceSeenAtMs())
        assertEquals(5_000L, FaceGazeStateStore.millisSinceLastFace(t0 + 5_000L))
    }

    @Test
    fun `reset clears lastFaceSeen`() {
        FaceGazeStateStore.update(
            FaceGazeSnapshot(0f, 0f, 0.9f, capturedAt = 50L),
        )
        FaceGazeStateStore.reset()
        assertNull(FaceGazeStateStore.lastFaceSeenAtMs())
        assertNull(FaceGazeStateStore.millisSinceLastFace(100L))
    }

    @Test
    fun `newer face updates lastFaceSeen`() {
        FaceGazeStateStore.update(FaceGazeSnapshot(0f, 0f, 0.9f, capturedAt = 10L))
        FaceGazeStateStore.update(FaceGazeSnapshot(0.2f, 0f, 0.9f, capturedAt = 40L))
        assertEquals(40L, FaceGazeStateStore.lastFaceSeenAtMs())
        assertTrue(FaceGazeStateStore.millisSinceLastFace(50L) == 10L)
    }
}
