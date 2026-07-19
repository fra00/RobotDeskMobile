package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
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
        assertTrue(text.contains("effimero") || text.contains("valenza di fondo") || text.contains("STILE RISPOSTA"))
    }
}
