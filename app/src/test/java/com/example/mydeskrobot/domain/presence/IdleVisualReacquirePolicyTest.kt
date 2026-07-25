package com.example.mydeskrobot.domain.presence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleVisualReacquirePolicyTest {

    private val sessionStart = 1_000_000L

    @Test
    fun `scans after five minutes without face from session start`() {
        assertTrue(
            IdleVisualReacquirePolicy.shouldScan(
                nowMs = sessionStart + IdleVisualReacquirePolicy.NO_FACE_THRESHOLD_MS,
                sessionStartedAtMs = sessionStart,
                lastFaceSeenAtMs = null,
                lastIdleAttemptAtMs = null,
                isWaitingForHotword = true,
                suppressForRobotContext = false,
            ),
        )
    }

    @Test
    fun `does not scan before threshold`() {
        assertFalse(
            IdleVisualReacquirePolicy.shouldScan(
                nowMs = sessionStart + IdleVisualReacquirePolicy.NO_FACE_THRESHOLD_MS - 1,
                sessionStartedAtMs = sessionStart,
                lastFaceSeenAtMs = null,
                lastIdleAttemptAtMs = null,
                isWaitingForHotword = true,
                suppressForRobotContext = false,
            ),
        )
    }

    @Test
    fun `uses lastFaceSeen when available`() {
        val lastFace = sessionStart + 60_000L
        assertFalse(
            IdleVisualReacquirePolicy.shouldScan(
                nowMs = lastFace + IdleVisualReacquirePolicy.NO_FACE_THRESHOLD_MS - 1,
                sessionStartedAtMs = sessionStart,
                lastFaceSeenAtMs = lastFace,
                lastIdleAttemptAtMs = null,
                isWaitingForHotword = true,
                suppressForRobotContext = false,
            ),
        )
        assertTrue(
            IdleVisualReacquirePolicy.shouldScan(
                nowMs = lastFace + IdleVisualReacquirePolicy.NO_FACE_THRESHOLD_MS,
                sessionStartedAtMs = sessionStart,
                lastFaceSeenAtMs = lastFace,
                lastIdleAttemptAtMs = null,
                isWaitingForHotword = true,
                suppressForRobotContext = false,
            ),
        )
    }

    @Test
    fun `respects cooldown after attempt`() {
        val now = sessionStart + IdleVisualReacquirePolicy.NO_FACE_THRESHOLD_MS + 1
        assertFalse(
            IdleVisualReacquirePolicy.shouldScan(
                nowMs = now,
                sessionStartedAtMs = sessionStart,
                lastFaceSeenAtMs = null,
                lastIdleAttemptAtMs = now - 1_000L,
                isWaitingForHotword = true,
                suppressForRobotContext = false,
            ),
        )
    }

    @Test
    fun `skips when not waiting for hotword`() {
        assertFalse(
            IdleVisualReacquirePolicy.shouldScan(
                nowMs = sessionStart + IdleVisualReacquirePolicy.NO_FACE_THRESHOLD_MS,
                sessionStartedAtMs = sessionStart,
                lastFaceSeenAtMs = null,
                lastIdleAttemptAtMs = null,
                isWaitingForHotword = false,
                suppressForRobotContext = false,
            ),
        )
    }

    @Test
    fun `skips when robot context suppresses`() {
        assertFalse(
            IdleVisualReacquirePolicy.shouldScan(
                nowMs = sessionStart + IdleVisualReacquirePolicy.NO_FACE_THRESHOLD_MS,
                sessionStartedAtMs = sessionStart,
                lastFaceSeenAtMs = null,
                lastIdleAttemptAtMs = null,
                isWaitingForHotword = true,
                suppressForRobotContext = true,
            ),
        )
    }
}
