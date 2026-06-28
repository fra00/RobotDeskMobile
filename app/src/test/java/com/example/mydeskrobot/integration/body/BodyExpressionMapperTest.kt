package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.domain.mood.RobotMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyExpressionMapperTest {

    private val neutral = RobotMood.NEUTRAL.copy(since = 0L)
    private val now = 1_000L

    @Test
    fun eyePoke_angry_turnsDisplayAway() {
        val current = RobotMood.fromValence(
            valence = -0.2f,
            since = now,
            reason = MoodReason.EYE_POKE,
            forceEmotion = RobotEmotion.ANGRY,
            forceIntensity = 0.65f,
        )
        val choreography = BodyExpressionMapper.resolve(neutral, current)
        assertNotNull(choreography)
        val joint = choreography!!.steps[0] as BodyMove.Joint
        assertEquals(BodyJoint.DISPLAY_PAN, joint.joint)
        assertEquals(-12, joint.position)
    }

    @Test
    fun eyePoke_confused_headRollClosed() {
        val current = RobotMood.fromValence(
            valence = -0.05f,
            since = now,
            reason = MoodReason.EYE_POKE,
            forceEmotion = RobotEmotion.CONFUSED,
            forceIntensity = 0.4f,
        )
        val choreography = BodyExpressionMapper.resolve(neutral, current)
        assertNotNull(choreography)
        assertEquals(2, choreography!!.steps.size)
        val peak = choreography.steps[0] as BodyMove.Joint
        val reset = choreography.steps[1] as BodyMove.Joint
        assertEquals(BodyJoint.HEAD_ROLL, peak.joint)
        assertEquals(0, reset.position)
    }

    @Test
    fun apology_neutral_goesHome() {
        val previous = RobotMood.fromValence(
            valence = -0.2f,
            since = 0L,
            reason = MoodReason.EYE_POKE,
            forceEmotion = RobotEmotion.ANGRY,
            forceIntensity = 0.7f,
        )
        val current = RobotMood.fromValence(
            valence = 0.05f,
            since = now,
            reason = MoodReason.USER_APOLOGY,
        )
        val choreography = BodyExpressionMapper.resolve(previous, current)
        assertTrue(choreography!!.steps[0] is BodyMove.Home)
    }

    @Test
    fun idleLong_bored_microFidgetReturnsToNeutral() {
        val current = RobotMood.fromValence(
            valence = -0.18f,
            since = now,
            reason = MoodReason.IDLE_LONG,
            forceEmotion = RobotEmotion.BORED,
            forceIntensity = 0.3f,
        )
        val choreography = BodyExpressionMapper.resolve(neutral, current)
        assertNotNull(choreography)
        assertEquals(2, choreography!!.steps.size)
        val reset = choreography.steps[1] as BodyMove.Joint
        assertEquals(0, reset.position)
    }

    @Test
    fun resolveMicroTick_lookAroundWhenBored() {
        val mood = RobotMood.fromValence(
            valence = -0.2f,
            since = 1_000L,
            reason = MoodReason.IDLE_LONG,
            forceEmotion = RobotEmotion.BORED,
            forceIntensity = 0.4f,
        )
        val choreography = BodyExpressionMapper.resolveMicroTick(mood, idleMinutes = 20)
        assertNotNull(choreography)
        assertEquals(3, choreography!!.steps.size)
    }

    @Test
    fun resolveMicroTick_tooSoon_returnsNull() {
        val mood = RobotMood.fromValence(
            valence = -0.2f,
            since = 1_000L,
            reason = MoodReason.IDLE_LONG,
            forceEmotion = RobotEmotion.BORED,
            forceIntensity = 0.4f,
        )
        assertNull(BodyExpressionMapper.resolveMicroTick(mood, idleMinutes = 5))
    }

    @Test
    fun enteringSleeping_returnsSleepPose() {
        val current = RobotMood.fromValence(
            valence = 0.1f,
            since = now,
            reason = MoodReason.NIGHT_TIME,
            forceEmotion = RobotEmotion.SLEEPING,
            forceIntensity = 1.0f,
        )
        val choreography = BodyExpressionMapper.resolve(neutral, current)
        assertEquals(BodyMove.SleepPose, choreography!!.steps.single())
    }

    @Test
    fun decay_fromEyePokeAnnoyance_goesHome() {
        val previous = RobotMood.fromValence(
            valence = -0.2f,
            since = 0L,
            reason = MoodReason.EYE_POKE,
            forceEmotion = RobotEmotion.ANGRY,
            forceIntensity = 0.7f,
        )
        val current = RobotMood.fromValence(
            valence = 0.1f,
            since = now,
            reason = null,
        )
        val choreography = BodyExpressionMapper.resolve(previous, current)
        assertTrue(choreography!!.steps.single() is BodyMove.Home)
    }

    @Test
    fun moodEmotionChange_toSad_returnsGesture() {
        val previous = RobotMood.NEUTRAL.copy(since = 0L)
        val current = RobotMood.fromValence(
            valence = -0.35f,
            since = now,
            reason = MoodReason.NEGATIVE_INTERACTION,
            forceEmotion = RobotEmotion.SAD,
            forceIntensity = 0.55f,
        )
        val choreography = BodyExpressionMapper.resolve(previous, current)
        assertNotNull(choreography)
        val first = choreography!!.steps.first() as BodyMove.Joint
        assertEquals(BodyJoint.HEAD_TILT, first.joint)
    }
}
