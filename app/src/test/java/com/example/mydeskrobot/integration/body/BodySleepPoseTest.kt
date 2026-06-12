package com.example.mydeskrobot.integration.body

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodySleepPoseTest {

    @Test
    fun isNearCenter_allJointsWithinTolerance() {
        val status = BodyStatus(
            joints = mapOf(
                "base_pan" to BodyJointState(position = 2),
                "display_pan" to BodyJointState(position = -1),
                "head_tilt" to BodyJointState(position = 0),
                "head_roll" to BodyJointState(position = 3),
            ),
        )
        assertTrue(BodySleepPose.isNearCenter(status))
    }

    @Test
    fun isNearCenter_jointOutOfTolerance() {
        val status = BodyStatus(
            joints = mapOf(
                "base_pan" to BodyJointState(position = 10),
                "display_pan" to BodyJointState(position = 0),
                "head_tilt" to BodyJointState(position = 0),
                "head_roll" to BodyJointState(position = 0),
            ),
        )
        assertFalse(BodySleepPose.isNearCenter(status))
    }

    @Test
    fun isHeadAtSleepTilt_withinTolerance() {
        val status = BodyStatus(
            joints = mapOf("head_tilt" to BodyJointState(position = -9)),
        )
        assertTrue(BodySleepPose.isHeadAtSleepTilt(status))
    }
}
