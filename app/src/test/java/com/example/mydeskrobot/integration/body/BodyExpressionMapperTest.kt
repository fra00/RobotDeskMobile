package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.domain.mood.RobotMood
import org.junit.Assert.assertEquals
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
        val moves = BodyExpressionMapper.resolve(neutral, current)
        assertEquals(1, moves.size)
        val joint = moves[0] as BodyMove.Joint
        assertEquals(BodyJoint.DISPLAY_PAN, joint.joint)
        assertEquals(-12, joint.delta)
    }

    @Test
    fun eyePoke_confused_headRoll() {
        val current = RobotMood.fromValence(
            valence = -0.05f,
            since = now,
            reason = MoodReason.EYE_POKE,
            forceEmotion = RobotEmotion.CONFUSED,
            forceIntensity = 0.4f,
        )
        val moves = BodyExpressionMapper.resolve(neutral, current)
        assertEquals(1, moves.size)
        val joint = moves[0] as BodyMove.Joint
        assertEquals(BodyJoint.HEAD_ROLL, joint.joint)
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
        val moves = BodyExpressionMapper.resolve(previous, current)
        assertTrue(moves[0] is BodyMove.Home)
    }

    @Test
    fun idleLong_bored_microFidget() {
        val current = RobotMood.fromValence(
            valence = -0.18f,
            since = now,
            reason = MoodReason.IDLE_LONG,
            forceEmotion = RobotEmotion.BORED,
            forceIntensity = 0.3f,
        )
        val moves = BodyExpressionMapper.resolve(neutral, current)
        assertEquals(1, moves.size)
        val joint = moves[0] as BodyMove.Joint
        assertEquals(BodyJoint.DISPLAY_PAN, joint.joint)
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
        val moves = BodyExpressionMapper.resolve(neutral, current)
        assertEquals(BodyMove.SleepPose, moves.single())
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
        val moves = BodyExpressionMapper.resolve(previous, current)
        assertTrue(moves.single() is BodyMove.Home)
    }
}
