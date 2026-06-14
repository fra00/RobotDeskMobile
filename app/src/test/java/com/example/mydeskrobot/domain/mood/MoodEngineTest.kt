package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoodEngineTest {

    private lateinit var engine: MoodEngine
    private val baseTime = 1_000_000_000L

    @Before
    fun setup() {
        engine = MoodEngine(MoodConfig(), MoodValenceConfig())
    }

    @Test
    fun `positive interaction increases valence to happy band`() {
        val neutral = neutralMood()
        val result = engine.evaluate(neutral, MoodTrigger.PositiveInteraction, baseTime + 1000)

        assertNotNull(result)
        assertEquals(0.3f, result!!.valence, 0.001f)
        assertEquals(RobotEmotion.HAPPY, result.baseEmotion)
        assertEquals(MoodReason.POSITIVE_INTERACTION, result.reason)
    }

    @Test
    fun `task completed increases valence`() {
        val neutral = neutralMood()
        val result = engine.evaluate(neutral, MoodTrigger.TaskCompletedUseful, baseTime + 1000)

        assertNotNull(result)
        assertEquals(0.18f, result!!.valence, 0.001f)
        assertEquals(MoodReason.TASK_COMPLETED, result.reason)
    }

    @Test
    fun `negative interaction decreases valence`() {
        val neutral = neutralMood()
        val result = engine.evaluate(neutral, MoodTrigger.NegativeInteraction, baseTime + 1000)

        assertNotNull(result)
        assertTrue(result!!.valence < MoodValenceConfig.DEFAULT_BASELINE)
        assertEquals(MoodReason.NEGATIVE_INTERACTION, result.reason)
    }

    @Test
    fun `positive interaction blocked when annoyed from eye poke`() {
        val annoyed = RobotMood.fromValence(
            valence = -0.2f,
            since = baseTime,
            reason = MoodReason.EYE_POKE,
        )
        val result = engine.evaluate(annoyed, MoodTrigger.PositiveInteraction, baseTime + 1000)
        assertNull(result)
    }

    @Test
    fun `eye poke tier 2 lowers valence and sets angry`() {
        val neutral = neutralMood()
        val result = engine.evaluate(neutral, MoodTrigger.EyePoked(tier = 2, count = 3), baseTime + 1000)

        assertNotNull(result)
        assertEquals(-0.1f, result!!.valence, 0.001f)
        assertEquals(MoodReason.EYE_POKE, result.reason)
    }

    @Test
    fun `user apology after eye poke improves valence`() {
        val annoyed = RobotMood.fromValence(
            valence = -0.2f,
            since = baseTime,
            reason = MoodReason.EYE_POKE,
        )
        val result = engine.evaluate(annoyed, MoodTrigger.UserApology, baseTime + 1000)

        assertNotNull(result)
        assertTrue(result!!.valence > -0.2f)
        assertEquals(MoodReason.USER_APOLOGY, result.reason)
    }

    @Test
    fun `idle 30 min transitions toward bored`() {
        val neutral = neutralMood()
        val result = engine.evaluate(neutral, MoodTrigger.IdleTime(30), baseTime + 30 * 60_000)

        assertNotNull(result)
        assertEquals(RobotEmotion.BORED, result!!.baseEmotion)
        assertEquals(MoodReason.IDLE_LONG, result.reason)
    }

    @Test
    fun `night mode transitions to sleeping without changing valence`() {
        val neutral = neutralMood()
        val result = engine.evaluate(neutral, MoodTrigger.NightMode, baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.SLEEPING, result!!.baseEmotion)
        assertEquals(MoodReason.NIGHT_TIME, result.reason)
        assertEquals(neutral.valence, result.valence, 0.001f)
    }

    @Test
    fun `day mode transitions sleeping back to derived mood`() {
        val sleeping = RobotMood.fromValence(
            valence = 0.1f,
            since = baseTime,
            reason = MoodReason.NIGHT_TIME,
            forceEmotion = RobotEmotion.SLEEPING,
            forceIntensity = 1f,
        )
        val result = engine.evaluate(sleeping, MoodTrigger.DayMode, baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.NEUTRAL, result!!.baseEmotion)
        assertNull(result.reason)
    }

    @Test
    fun `valence decays toward baseline after positive interaction`() {
        val config = MoodConfig(happyDecayMinutes = 20)
        val engine = MoodEngine(config, MoodValenceConfig())
        val happy = RobotMood.fromValence(
            valence = 0.35f,
            since = baseTime,
            reason = MoodReason.POSITIVE_INTERACTION,
        )
        val decayed = engine.checkDecay(happy, baseTime + 20 * 60_000)

        assertNotNull(decayed)
        assertTrue(decayed!!.valence < happy.valence)
        assertTrue(decayed.valence >= happy.baseline)
    }

    @Test
    fun `reminder soon triggers surprised`() {
        val neutral = neutralMood()
        val result = engine.evaluate(neutral, MoodTrigger.ReminderSoon(10), baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.SURPRISED, result!!.baseEmotion)
        assertEquals(MoodReason.REMINDER_URGENT, result.reason)
    }

    private fun neutralMood(): RobotMood =
        RobotMood.fromValence(
            valence = MoodValenceConfig.DEFAULT_BASELINE,
            since = baseTime,
            reason = null,
        )
}
