package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodPromptFormatterTest {

    @Test
    fun `formats authoritative section with valence and eye poke hint`() {
        val mood = RobotMood.fromValence(
            valence = -0.2f,
            since = 0L,
            reason = MoodReason.EYE_POKE,
            forceEmotion = RobotEmotion.ANGRY,
            forceIntensity = 0.7f,
        )
        val text = MoodPromptFormatter.format(mood)

        assertTrue(text.contains("STATO ROBOT"))
        assertTrue(text.contains("Valenza:"))
        assertTrue(text.contains("angry (70%)"))
        assertTrue(text.contains("poke_occhi"))
        assertTrue(text.contains("Espressione attuale"))
        assertTrue(text.contains("Coerenza:"))
    }

    @Test
    fun `includes active ephemeral expression for coherence`() {
        val mood = RobotMood.fromValence(
            valence = 0.2f,
            since = 0L,
            reason = null,
        )
        val ephemeral = EphemeralExpression(
            emotion = RobotEmotion.BORED,
            intensity = 0.55f,
            expiresAt = 50_000L,
        )
        val text = MoodPromptFormatter.format(mood, ephemeral = ephemeral, now = 10_000L)

        assertTrue(text.contains("Espressione attuale (occhi, effimera): bored"))
        assertTrue(text.contains("non dire che va tutto bene"))
    }

    @Test
    fun `expired ephemeral is not listed as current face`() {
        val mood = RobotMood.fromValence(valence = 0.1f, since = 0L, reason = null)
        val ephemeral = EphemeralExpression(
            emotion = RobotEmotion.BORED,
            intensity = 0.55f,
            expiresAt = 5_000L,
        )
        val text = MoodPromptFormatter.format(mood, ephemeral = ephemeral, now = 10_000L)

        assertFalse(text.contains("effimera): bored"))
        assertTrue(text.contains("nessuna effimera attiva"))
    }
}
