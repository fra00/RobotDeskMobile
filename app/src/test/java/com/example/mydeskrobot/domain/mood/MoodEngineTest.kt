package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MoodEngineTest {

    private lateinit var engine: MoodEngine
    private val baseTime = 1_000_000_000L

    @Before
    fun setup() {
        engine = MoodEngine(MoodConfig())
    }

    @Test
    fun `idle 30 min transitions neutral to bored`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.IdleTime(30), baseTime + 30 * 60_000)

        assertNotNull(result)
        assertEquals(RobotEmotion.BORED, result!!.baseEmotion)
        assertEquals(0.3f, result.intensity)
        assertEquals(MoodReason.IDLE_LONG, result.reason)
    }

    @Test
    fun `idle less than 30 min does not transition neutral`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.IdleTime(29), baseTime + 29 * 60_000)

        assertNull(result)
    }

    @Test
    fun `idle 90 min transitions bored to drowsy`() {
        val bored = RobotMood(RobotEmotion.BORED, 0.3f, baseTime, MoodReason.IDLE_LONG)
        val result = engine.evaluate(bored, MoodTrigger.IdleTime(90), baseTime + 90 * 60_000)

        assertNotNull(result)
        assertEquals(RobotEmotion.DROWSY, result!!.baseEmotion)
        assertEquals(0.5f, result.intensity)
        assertEquals(MoodReason.IDLE_VERY_LONG, result.reason)
    }

    @Test
    fun `positive interaction transitions to happy`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.PositiveInteraction, baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.HAPPY, result!!.baseEmotion)
        assertEquals(0.4f, result.intensity)
        assertEquals(MoodReason.POSITIVE_INTERACTION, result.reason)
    }

    @Test
    fun `positive interaction refreshes happy mood timestamp`() {
        val happy = RobotMood(RobotEmotion.HAPPY, 0.4f, baseTime, MoodReason.POSITIVE_INTERACTION)
        val newTime = baseTime + 10 * 60_000
        val result = engine.evaluate(happy, MoodTrigger.PositiveInteraction, newTime)

        assertNotNull(result)
        assertEquals(RobotEmotion.HAPPY, result!!.baseEmotion)
        assertEquals(newTime, result.since)
    }

    @Test
    fun `positive interaction does not happy when annoyed from eye poke`() {
        val annoyed = RobotMood(RobotEmotion.ANGRY, 0.7f, baseTime, MoodReason.EYE_POKE)
        val result = engine.evaluate(annoyed, MoodTrigger.PositiveInteraction, baseTime + 1000)

        assertNull(result)
    }

    @Test
    fun `eye poke tier 1 transitions to confused`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.EyePoked(tier = 1, count = 1), baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.CONFUSED, result!!.baseEmotion)
        assertEquals(MoodReason.EYE_POKE, result.reason)
    }

    @Test
    fun `eye poke tier 2 transitions to angry`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.EyePoked(tier = 2, count = 3), baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.ANGRY, result!!.baseEmotion)
        assertEquals(0.65f, result.intensity)
    }

    @Test
    fun `user apology softens angry from eye poke`() {
        val angry = RobotMood(RobotEmotion.ANGRY, 0.7f, baseTime, MoodReason.EYE_POKE)
        val result = engine.evaluate(angry, MoodTrigger.UserApology, baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.CONFUSED, result!!.baseEmotion)
        assertEquals(MoodReason.USER_APOLOGY, result.reason)
    }

    @Test
    fun `user apology on confused from eye poke goes neutral`() {
        val confused = RobotMood(RobotEmotion.CONFUSED, 0.4f, baseTime, MoodReason.EYE_POKE)
        val result = engine.evaluate(confused, MoodTrigger.UserApology, baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.NEUTRAL, result!!.baseEmotion)
    }

    @Test
    fun `assistant declared neutral aligns angry eye poke mood`() {
        val angry = RobotMood(RobotEmotion.ANGRY, 0.7f, baseTime, MoodReason.EYE_POKE)
        val result = engine.evaluate(
            angry,
            MoodTrigger.AssistantDeclaredEmotion(RobotEmotion.NEUTRAL),
            baseTime + 1000,
        )

        assertNotNull(result)
        assertEquals(RobotEmotion.NEUTRAL, result!!.baseEmotion)
        assertNull(result.reason)
    }

    @Test
    fun `assistant declared happy does not override active eye poke annoyance`() {
        val angry = RobotMood(RobotEmotion.ANGRY, 0.7f, baseTime, MoodReason.EYE_POKE)
        val result = engine.evaluate(
            angry,
            MoodTrigger.AssistantDeclaredEmotion(RobotEmotion.HAPPY),
            baseTime + 1000,
        )

        assertNull(result)
    }

    @Test
    fun `eye poke annoyance decays to neutral`() {
        val config = MoodConfig(eyePokeAnnoyanceDecayMinutes = 8)
        val engine = MoodEngine(config)
        val angry = RobotMood(RobotEmotion.ANGRY, 0.7f, baseTime, MoodReason.EYE_POKE)
        val decayed = engine.checkDecay(angry, baseTime + 8 * 60_000)

        assertNotNull(decayed)
        assertEquals(RobotEmotion.NEUTRAL, decayed!!.baseEmotion)
    }

    @Test
    fun `night mode transitions to sleeping`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.NightMode, baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.SLEEPING, result!!.baseEmotion)
        assertEquals(1.0f, result.intensity)
        assertEquals(MoodReason.NIGHT_TIME, result.reason)
    }

    @Test
    fun `day mode transitions sleeping to neutral`() {
        val sleeping = RobotMood(RobotEmotion.SLEEPING, 1.0f, baseTime, MoodReason.NIGHT_TIME)
        val result = engine.evaluate(sleeping, MoodTrigger.DayMode, baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.NEUTRAL, result!!.baseEmotion)
        assertEquals(0.5f, result.intensity)
        assertNull(result.reason)
    }

    @Test
    fun `day mode transitions drowsy to neutral`() {
        val drowsy = RobotMood(RobotEmotion.DROWSY, 0.5f, baseTime, MoodReason.IDLE_VERY_LONG)
        val result = engine.evaluate(drowsy, MoodTrigger.DayMode, baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.NEUTRAL, result!!.baseEmotion)
    }

    @Test
    fun `day mode does not affect neutral`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.DayMode, baseTime + 1000)

        assertNull(result)
    }

    @Test
    fun `llm happy emotion transitions mood`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.LlmEmotion(RobotEmotion.HAPPY), baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.HAPPY, result!!.baseEmotion)
        assertEquals(0.6f, result.intensity)
        assertEquals(MoodReason.POSITIVE_INTERACTION, result.reason)
    }

    @Test
    fun `llm listening emotion does not transition`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.LlmEmotion(RobotEmotion.LISTENING), baseTime + 1000)

        assertNull(result)
    }

    @Test
    fun `llm speaking emotion does not transition`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.LlmEmotion(RobotEmotion.SPEAKING), baseTime + 1000)

        assertNull(result)
    }

    @Test
    fun `llm angry emotion transitions with negative reason`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.LlmEmotion(RobotEmotion.ANGRY), baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.ANGRY, result!!.baseEmotion)
        assertEquals(0.7f, result.intensity)
        assertEquals(MoodReason.NEGATIVE_INTERACTION, result.reason)
    }

    @Test
    fun `reminder soon triggers surprised emotion`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.ReminderSoon(10), baseTime + 1000)

        assertNotNull(result)
        assertEquals(RobotEmotion.SURPRISED, result!!.baseEmotion)
        assertEquals(MoodReason.REMINDER_URGENT, result.reason)
    }

    @Test
    fun `reminder far away does not trigger`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val result = engine.evaluate(neutral, MoodTrigger.ReminderSoon(20), baseTime + 1000)

        assertNull(result)
    }

    @Test
    fun `sleeping prevents idle transition`() {
        val sleeping = RobotMood(RobotEmotion.SLEEPING, 1.0f, baseTime, MoodReason.NIGHT_TIME)
        val result = engine.evaluate(sleeping, MoodTrigger.IdleTime(100), baseTime + 100 * 60_000)

        assertNull(result)
    }

    @Test
    fun `happy prevents idle transition`() {
        val happy = RobotMood(RobotEmotion.HAPPY, 0.4f, baseTime, MoodReason.POSITIVE_INTERACTION)
        val result = engine.evaluate(happy, MoodTrigger.IdleTime(30), baseTime + 30 * 60_000)

        assertNull(result)
    }

    @Test
    fun `happy decays after configured time`() {
        val config = MoodConfig(happyDecayMinutes = 20)
        val engine = MoodEngine(config)
        val happy = RobotMood(RobotEmotion.HAPPY, 0.4f, baseTime, MoodReason.POSITIVE_INTERACTION)
        val decayed = engine.checkDecay(happy, baseTime + 20 * 60_000)

        assertNotNull(decayed)
        assertEquals(RobotEmotion.NEUTRAL, decayed!!.baseEmotion)
    }

    @Test
    fun `happy does not decay before configured time`() {
        val config = MoodConfig(happyDecayMinutes = 20)
        val engine = MoodEngine(config)
        val happy = RobotMood(RobotEmotion.HAPPY, 0.4f, baseTime, MoodReason.POSITIVE_INTERACTION)
        val decayed = engine.checkDecay(happy, baseTime + 19 * 60_000)

        assertNull(decayed)
    }

    @Test
    fun `neutral does not decay`() {
        val neutral = RobotMood(RobotEmotion.NEUTRAL, 0.5f, baseTime, null)
        val decayed = engine.checkDecay(neutral, baseTime + 100 * 60_000)

        assertNull(decayed)
    }

    @Test
    fun `sleeping prevents reminder trigger`() {
        val sleeping = RobotMood(RobotEmotion.SLEEPING, 1.0f, baseTime, MoodReason.NIGHT_TIME)
        val result = engine.evaluate(sleeping, MoodTrigger.ReminderSoon(5), baseTime + 1000)

        assertNull(result)
    }

    @Test
    fun `already urgent reminder does not trigger again`() {
        val urgent = RobotMood(RobotEmotion.SURPRISED, 0.6f, baseTime, MoodReason.REMINDER_URGENT)
        val result = engine.evaluate(urgent, MoodTrigger.ReminderSoon(5), baseTime + 1000)

        assertNull(result)
    }
}
