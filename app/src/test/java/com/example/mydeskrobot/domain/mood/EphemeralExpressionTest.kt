package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EphemeralExpressionTest {

    @Test
    fun `creates ephemeral for angry with ttl`() {
        val now = 1_000_000L
        val expr = EphemeralExpressionPolicy.create(RobotEmotion.ANGRY, now)

        assertEquals(RobotEmotion.ANGRY, expr?.emotion)
        assertTrue(expr!!.isActive(now + 15_000))
        assertFalse(expr.isActive(now + 31_000))
    }

    @Test
    fun `neutral emotion does not create ephemeral`() {
        assertNull(EphemeralExpressionPolicy.create(RobotEmotion.NEUTRAL))
    }

    @Test
    fun `llm angry ephemeral does not imply valence change`() {
        val wellbeing = RobotMood.fromValence(valence = 0.35f, reason = null)
        val now = 1_000_000L
        val ephemeral = EphemeralExpressionPolicy.create(RobotEmotion.ANGRY, now)

        assertEquals(0.35f, wellbeing.valence, 0.001f)
        assertEquals(RobotEmotion.ANGRY, ephemeral?.emotion)
    }
}
