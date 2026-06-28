package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmotionGestureMapperTest {

    @Test
    fun sad_returnsHeadDownAndBack() {
        val choreography = EmotionGestureMapper.resolve(RobotEmotion.SAD, 0.6f)
        assertNotNull(choreography)
        assertEquals(2, choreography!!.steps.size)
        val down = choreography.steps[0] as BodyMove.Joint
        val up = choreography.steps[1] as BodyMove.Joint
        assertEquals(BodyJoint.HEAD_TILT, down.joint)
        assertEquals(true, (down.position ?: 0) < 0)
        assertEquals(0, up.position)
    }

    @Test
    fun happy_returnsNod() {
        val choreography = EmotionGestureMapper.resolve(RobotEmotion.HAPPY, 0.7f)
        assertNotNull(choreography)
        assertEquals(2, choreography!!.steps.size)
    }

    @Test
    fun surprised_returnsClosedLookAround() {
        val choreography = EmotionGestureMapper.resolve(RobotEmotion.SURPRISED, 0.65f)
        assertNotNull(choreography)
        assertEquals(3, choreography!!.steps.size)
        val last = choreography.steps.last() as BodyMove.Joint
        assertEquals(0, last.position)
    }

    @Test
    fun neutral_returnsNull() {
        assertNull(EmotionGestureMapper.resolve(RobotEmotion.NEUTRAL))
        assertNull(EmotionGestureMapper.resolve(RobotEmotion.SPEAKING))
    }
}
