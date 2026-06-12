package com.example.mydeskrobot.ui.eyes

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeExpressionMapperTest {

    @Test
    fun `angry intensity increases rotation`() {
        val low = EyeExpressionMapper.map(RobotEmotion.ANGRY, 0.3f)
        val high = EyeExpressionMapper.map(RobotEmotion.ANGRY, 0.9f)

        assertTrue(kotlin.math.abs(high.left.geometry.rotationDeg) > kotlin.math.abs(low.left.geometry.rotationDeg))
        assertEquals(EyeMotion.SHAKE, high.left.motion)
        assertTrue(high.left.motionAmplitude > low.left.motionAmplitude)
    }

    @Test
    fun `sleeping hides pupil`() {
        val spec = EyeExpressionMapper.map(RobotEmotion.SLEEPING, 0.5f)
        assertFalse(spec.left.pupil.visible)
        assertEquals(EyebrowStyle.NONE, spec.left.eyebrow.style)
    }

    @Test
    fun `confused has asymmetric eyes`() {
        val spec = EyeExpressionMapper.map(RobotEmotion.CONFUSED, 0.6f)
        assertNotEquals(spec.left.geometry.rotationDeg, spec.right.geometry.rotationDeg)
        assertEquals(EyeMotion.PUPIL_DRIFT, spec.left.motion)
    }

    @Test
    fun `angry has angry eyebrows`() {
        val spec = EyeExpressionMapper.map(RobotEmotion.ANGRY, 0.8f)
        assertEquals(EyebrowStyle.ANGRY_V, spec.left.eyebrow.style)
    }

    @Test
    fun `happy has bounce motion`() {
        val spec = EyeExpressionMapper.map(RobotEmotion.HAPPY, 0.7f)
        assertEquals(EyeMotion.BOUNCE, spec.left.motion)
    }

    @Test
    fun `surprised enables pop`() {
        val spec = EyeExpressionMapper.map(RobotEmotion.SURPRISED, 0.5f)
        assertTrue(spec.surprisedPop)
    }
}
