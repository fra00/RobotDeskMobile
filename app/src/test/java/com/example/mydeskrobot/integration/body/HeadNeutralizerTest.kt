package com.example.mydeskrobot.integration.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadNeutralizerTest {

    @Test
    fun jointsNeedingNeutral_detectsOffCenterHead() {
        val status = BodyStatus(
            joints = mapOf(
                "head_tilt" to BodyJointState(position = 12),
                "head_roll" to BodyJointState(position = 0),
                "display_pan" to BodyJointState(position = -6),
            ),
        )
        val targets = HeadNeutralizer.jointsNeedingNeutral(status)
        assertEquals(2, targets.size)
        assertEquals(0, targets[BodyJoint.HEAD_TILT])
        assertEquals(0, targets[BodyJoint.DISPLAY_PAN])
        assertTrue(BodyJoint.HEAD_ROLL !in targets)
    }

    @Test
    fun jointsNeedingNeutral_allCentered_returnsEmpty() {
        val status = BodyStatus(
            joints = mapOf(
                "head_tilt" to BodyJointState(position = 2),
                "head_roll" to BodyJointState(position = -3),
                "display_pan" to BodyJointState(position = 1),
            ),
        )
        assertTrue(HeadNeutralizer.jointsNeedingNeutral(status).isEmpty())
    }
}
