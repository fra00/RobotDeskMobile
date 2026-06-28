package com.example.mydeskrobot.integration.presence

import com.example.mydeskrobot.domain.presence.FaceGazeSnapshot
import com.example.mydeskrobot.integration.body.BodyJoint
import com.example.mydeskrobot.integration.body.BodyMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionCenteringPlannerTest {

    @Test
    fun centeredFace_returnsNoHorizontalMove() {
        val gaze = FaceGazeSnapshot(
            horizontalOffset = 0.05f,
            verticalOffset = -0.04f,
            confidence = 0.8f,
        )
        assertTrue(AttentionCenteringPlanner.planHorizontalMove(gaze) == null)
    }

    @Test
    fun faceRight_invertedPan_movesNegative() {
        val gaze = FaceGazeSnapshot(
            horizontalOffset = 0.3f,
            verticalOffset = 0f,
            confidence = 0.8f,
        )
        val pan = AttentionCenteringPlanner.planHorizontalMove(gaze, panSign = -1)!!
        assertEquals(BodyJoint.BASE_PAN, pan.joint)
        assertTrue((pan.position ?: 0) < 0)
    }

    @Test
    fun faceRight_positivePan_movesPositive() {
        val gaze = FaceGazeSnapshot(
            horizontalOffset = 0.3f,
            verticalOffset = 0f,
            confidence = 0.8f,
        )
        val pan = AttentionCenteringPlanner.planHorizontalMove(gaze, panSign = 1)!!
        assertTrue(pan.position!! > 0)
    }

    @Test
    fun faceLow_usesHeadTiltDown() {
        val gaze = FaceGazeSnapshot(
            horizontalOffset = 0f,
            verticalOffset = 0.35f,
            confidence = 0.7f,
        )
        val tilt = AttentionCenteringPlanner.planVerticalMove(gaze)
        assertNotNull(tilt)
        assertEquals(BodyJoint.HEAD_TILT, tilt!!.joint)
        assertTrue((tilt.position ?: 0) < 0)
    }

    @Test
    fun slowerPanSpeed() {
        val gaze = FaceGazeSnapshot(horizontalOffset = 0.25f, verticalOffset = 0f, confidence = 0.8f)
        val pan = AttentionCenteringPlanner.planHorizontalMove(gaze)!!
        assertEquals(16, pan.speed ?: 0)
        assertTrue((pan.speed ?: 0) <= 18)
    }

    @Test
    fun scanPan_movesInDirection() {
        val move = AttentionCenteringPlanner.planScanPanMove(currentPan = 0, directionSign = -1)!!
        assertEquals(BodyJoint.BASE_PAN, move.joint)
        assertEquals(-14, move.position)
    }

    @Test
    fun scanPan_atLimit_returnsNull() {
        assertTrue(AttentionCenteringPlanner.planScanPanMove(currentPan = 45, directionSign = 1) == null)
    }
}
