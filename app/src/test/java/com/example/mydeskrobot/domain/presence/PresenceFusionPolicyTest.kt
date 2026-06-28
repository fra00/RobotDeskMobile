package com.example.mydeskrobot.domain.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceFusionPolicyTest {

    private val policy = PresenceFusionPolicy(
        faceConfidenceThreshold = 0.6f,
        poseConfidenceThreshold = 0.5f,
        absentStreakRequired = 3,
    )

    @Test
    fun fuse_present_whenFaceConfident() {
        val result = policy.fuse(
            PresenceFrameSignals(
                facesInRoi = 1,
                maxFaceConfidence = 0.8f,
            ),
        )
        assertEquals(DeskOccupancyState.PRESENT, result.state)
    }

    @Test
    fun fuse_absent_afterStreak() {
        repeat(2) {
            val mid = policy.fuse(PresenceFrameSignals())
            assertEquals(DeskOccupancyState.UNCERTAIN, mid.state)
        }
        val absent = policy.fuse(PresenceFrameSignals())
        assertEquals(DeskOccupancyState.ABSENT, absent.state)
    }

    @Test
    fun fuse_uncertain_onWeakFace() {
        val result = policy.fuse(
            PresenceFrameSignals(
                facesInRoi = 1,
                maxFaceConfidence = 0.4f,
            ),
        )
        assertEquals(DeskOccupancyState.UNCERTAIN, result.state)
    }
}
