package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EphemeralExpressionTest {

    @Test
    fun `creates ephemeral for angry with ttl`() {
        val now = 1_000_000L
        val expr = EphemeralExpressionPolicy.create(RobotEmotion.ANGRY, now = now)

        assertEquals(RobotEmotion.ANGRY, expr?.emotion)
        assertTrue(expr!!.isActive(now + 15_000))
        assertFalse(expr.isActive(now + 31_000))
    }

    @Test
    fun `neutral emotion creates short ephemeral`() {
        val now = 1_000_000L
        val expr = EphemeralExpressionPolicy.create(RobotEmotion.NEUTRAL, now = now)

        assertNotNull(expr)
        assertEquals(RobotEmotion.NEUTRAL, expr!!.emotion)
        assertEquals(0.45f, expr.intensity, 0.001f)
        assertTrue(expr.isActive(now + 10_000))
        assertFalse(expr.isActive(now + 15_000))
    }

    @Test
    fun `llm sad emotion lowers valence via mood engine`() {
        val engine = MoodEngine()
        val neutral = RobotMood.fromValence(
            valence = MoodValenceConfig.DEFAULT_BASELINE,
            since = 1_000_000L,
            reason = null,
        )
        val result = engine.evaluate(neutral, MoodTrigger.LlmEmotion(RobotEmotion.SAD), 1_000_100L)

        assertNotNull(result)
        assertTrue(result!!.valence < neutral.valence)
        assertEquals(MoodReason.LLM_EXPRESSION, result.reason)
    }
}
