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
    fun `llm happy full tier increases valence`() {
        val neutral = neutralMood()
        val result = engine.evaluate(
            neutral,
            MoodTrigger.LlmEmotion(RobotEmotion.HAPPY, LlmEmotionValenceTier.FULL),
            baseTime + 1000,
        )

        assertNotNull(result)
        assertEquals(
            MoodValenceConfig.DEFAULT_BASELINE + MoodValenceConfig.LLM_HAPPY_DELTA,
            result!!.valence,
            0.001f,
        )
        assertEquals(MoodReason.LLM_EXPRESSION, result.reason)
        assertEquals(neutral.lastDecayAtMs, result.lastDecayAtMs)
    }

    @Test
    fun `task completed increases valence`() {
        val neutral = neutralMood()
        val result = engine.evaluate(neutral, MoodTrigger.TaskCompletedUseful, baseTime + 1000)

        assertNotNull(result)
        assertEquals(0.18f, result!!.valence, 0.001f)
        assertEquals(MoodReason.TASK_COMPLETED, result.reason)
        assertEquals(neutral.lastDecayAtMs, result.lastDecayAtMs)
    }

    @Test
    fun `llm angry emotion decreases valence`() {
        val neutral = neutralMood()
        val result = engine.evaluate(
            neutral,
            MoodTrigger.LlmEmotion(RobotEmotion.ANGRY),
            baseTime + 1000,
        )

        assertNotNull(result)
        assertEquals(
            MoodValenceConfig.DEFAULT_BASELINE + MoodValenceConfig.LLM_ANGRY_DELTA,
            result!!.valence,
            0.001f,
        )
        assertEquals(MoodReason.LLM_EXPRESSION, result.reason)
    }

    @Test
    fun `llm happy blocked when annoyed from eye poke`() {
        val annoyed = RobotMood.fromValence(
            valence = -0.2f,
            since = baseTime,
            reason = MoodReason.EYE_POKE,
        )
        val result = engine.evaluate(
            annoyed,
            MoodTrigger.LlmEmotion(RobotEmotion.HAPPY, LlmEmotionValenceTier.FULL),
            baseTime + 1000,
        )
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
        assertEquals(neutral.lastDecayAtMs, result.lastDecayAtMs)
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
    fun `positive valence decays toward baseline after happy decay window`() {
        val config = MoodConfig(happyDecayMinutes = 5)
        val engine = MoodEngine(config, MoodValenceConfig())
        val happy = RobotMood.fromValence(
            valence = 0.35f,
            since = baseTime,
            reason = MoodReason.LLM_EXPRESSION,
            lastDecayAtMs = baseTime,
        )
        val decayed = engine.checkDecay(happy, baseTime + 5 * 60_000)

        assertNotNull(decayed)
        assertTrue(decayed!!.valence < happy.valence)
        assertTrue(decayed.valence >= happy.baseline)
        assertEquals(baseTime + 5 * 60_000, decayed.lastDecayAtMs)
    }

    @Test
    fun `negative valence drifts toward baseline after sad decay window`() {
        val config = MoodConfig(sadDecayMinutes = 6)
        val engine = MoodEngine(config, MoodValenceConfig())
        val sad = RobotMood.fromValence(
            valence = -0.3f,
            since = baseTime,
            reason = MoodReason.LLM_EXPRESSION,
            lastDecayAtMs = baseTime,
        )
        val decayed = engine.checkDecay(sad, baseTime + 6 * 60_000)

        assertNotNull(decayed)
        assertTrue(decayed!!.valence > sad.valence)
        assertTrue(decayed.valence <= sad.baseline)
    }

    @Test
    fun `negative valence does not drift before sad decay window`() {
        val config = MoodConfig(sadDecayMinutes = 6)
        val engine = MoodEngine(config, MoodValenceConfig())
        val sad = RobotMood.fromValence(
            valence = -0.3f,
            since = baseTime,
            reason = MoodReason.LLM_EXPRESSION,
            lastDecayAtMs = baseTime,
        )
        assertNull(engine.checkDecay(sad, baseTime + 5 * 60_000))
    }

    @Test
    fun `decay uses lastDecayAt even when since is recent`() {
        val happy = RobotMood.fromValence(
            valence = 0.5f,
            since = baseTime + 20 * 60_000,
            reason = MoodReason.VOICE_TURN_PRESENCE,
            lastDecayAtMs = baseTime,
        )
        val decayed = engine.checkDecay(happy, baseTime + 5 * 60_000)

        assertNotNull(decayed)
        assertTrue(decayed!!.valence < happy.valence)
    }

    @Test
    fun `voice turn presence does not delay next decay step`() {
        val happy = RobotMood.fromValence(
            valence = 0.5f,
            since = baseTime,
            reason = MoodReason.LLM_EXPRESSION,
            lastDecayAtMs = baseTime,
        )
        val afterVoice = engine.evaluate(
            happy,
            MoodTrigger.VoiceTurnPresence(0.04f),
            baseTime + 2 * 60_000,
        )
        assertNotNull(afterVoice)
        assertEquals(happy.lastDecayAtMs, afterVoice!!.lastDecayAtMs)

        val decayed = engine.checkDecay(afterVoice, baseTime + 5 * 60_000)
        assertNotNull(decayed)
        assertTrue(decayed!!.valence < afterVoice.valence)
    }

    @Test
    fun `idle bored valence can drift toward baseline`() {
        val bored = RobotMood.fromValence(
            valence = -0.2f,
            since = baseTime,
            reason = MoodReason.IDLE_LONG,
            lastDecayAtMs = baseTime,
        )
        val decayed = engine.checkDecay(bored, baseTime + 6 * 60_000)

        assertNotNull(decayed)
        assertTrue(decayed!!.valence > bored.valence)
        assertTrue(decayed.valence <= bored.baseline)
    }

    @Test
    fun `idle reason clears near baseline after drift`() {
        val bored = RobotMood.fromValence(
            valence = MoodValenceConfig.DEFAULT_BASELINE - 0.05f,
            since = baseTime,
            reason = MoodReason.IDLE_LISTENING,
            lastDecayAtMs = baseTime,
        )
        val decayed = engine.checkDecay(bored, baseTime + 6 * 60_000)

        assertNotNull(decayed)
        assertNull(decayed!!.reason)
        assertEquals(MoodValenceConfig.DEFAULT_BASELINE, decayed.valence, 0.02f)
    }

    @Test
    fun `peak happy reaches baseline within 30 min decay clock`() {
        val config = MoodConfig(happyDecayMinutes = 5)
        val valenceConfig = MoodValenceConfig(decayTowardBaseline = 0.15f)
        val engine = MoodEngine(config, valenceConfig)
        var mood = RobotMood.fromValence(
            valence = 0.85f,
            since = baseTime,
            reason = MoodReason.LLM_EXPRESSION,
            lastDecayAtMs = baseTime,
        )
        // 5 steps × 5 min = 25 min clock; 0.85 − 5×0.15 = 0.10 baseline
        var now = baseTime
        repeat(5) {
            now += 5 * 60_000L
            val next = engine.checkDecay(mood, now)
            assertNotNull(next)
            mood = next!!
        }
        assertEquals(MoodValenceConfig.DEFAULT_BASELINE, mood.valence, 0.02f)
        assertTrue(now - baseTime <= 30 * 60_000L)
    }

    @Test
    fun `hotword listening idle transitions to bored listening reason`() {
        val neutral = neutralMood()
        val result = engine.evaluate(
            neutral,
            MoodTrigger.HotwordListeningIdle(10),
            baseTime + 10 * 60_000,
        )

        assertNotNull(result)
        assertEquals(RobotEmotion.BORED, result!!.baseEmotion)
        assertEquals(MoodReason.IDLE_LISTENING, result.reason)
    }

    @Test
    fun `voice turn presence increases valence slightly`() {
        val neutral = neutralMood()
        val result = engine.evaluate(
            neutral,
            MoodTrigger.VoiceTurnPresence(0.04f),
            baseTime + 1000,
        )

        assertNotNull(result)
        assertEquals(0.14f, result!!.valence, 0.001f)
        assertEquals(MoodReason.VOICE_TURN_PRESENCE, result.reason)
    }

    @Test
    fun `routine happy llm emotion does not shift valence`() {
        val neutral = neutralMood()
        val result = engine.evaluate(
            neutral,
            MoodTrigger.LlmEmotion(RobotEmotion.HAPPY, LlmEmotionValenceTier.ROUTINE),
            baseTime + 1000,
        )

        assertNull(result)
    }

    private fun neutralMood(): RobotMood =
        RobotMood.fromValence(
            valence = MoodValenceConfig.DEFAULT_BASELINE,
            since = baseTime,
            reason = null,
        )
}
