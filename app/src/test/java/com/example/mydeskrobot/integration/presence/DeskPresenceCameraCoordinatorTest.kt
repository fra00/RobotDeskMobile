package com.example.mydeskrobot.integration.presence

import com.example.mydeskrobot.domain.presence.DeskOccupancy
import com.example.mydeskrobot.domain.presence.DeskOccupancyState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class DeskPresenceCameraCoordinatorTest {

    @Before
    @After
    fun clearBind() {
        // Ensure no leftover session from other tests.
        DeskPresenceCameraCoordinator.publishCameraActive(false)
    }

    @Test
    fun `withCamera without bind runs block`() = runTest {
        var ran = false
        val result = DeskPresenceCameraCoordinator.withCamera {
            ran = true
            42
        }
        assertTrue(ran)
        assertEquals(42, result)
    }

    @Test
    fun `awaitOccupancySample without bind returns null`() = runTest {
        assertNull(DeskPresenceCameraCoordinator.awaitOccupancySample(timeoutMs = 50L))
    }

    @Test
    fun `withCamera uses bound session`() = runTest {
        val fake = FakeSession()
        DeskPresenceCameraCoordinator.bind(fake)
        try {
            val result = DeskPresenceCameraCoordinator.withCamera {
                assertTrue(DeskPresenceCameraCoordinator.cameraActive.value)
                "ok"
            }
            assertEquals("ok", result)
            assertEquals(1, fake.withCameraCalls)
            assertFalse(DeskPresenceCameraCoordinator.cameraActive.value)
        } finally {
            DeskPresenceCameraCoordinator.unbind(fake)
        }
    }

    private class FakeSession : DeskPresenceCameraSession {
        var withCameraCalls = 0

        override suspend fun <T> withCamera(block: suspend () -> T): T {
            withCameraCalls++
            DeskPresenceCameraCoordinator.publishCameraActive(true)
            return try {
                block()
            } finally {
                DeskPresenceCameraCoordinator.publishCameraActive(false)
            }
        }

        override suspend fun awaitOccupancySample(timeoutMs: Long): DeskOccupancy =
            DeskOccupancy(state = DeskOccupancyState.PRESENT, confidence = 1f, updatedAt = 1L)
    }
}
