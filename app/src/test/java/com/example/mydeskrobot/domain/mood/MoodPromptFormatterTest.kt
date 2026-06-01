package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodPromptFormatterTest {

    @Test
    fun `formats authoritative section with eye poke hint`() {
        val mood = RobotMood(
            baseEmotion = RobotEmotion.ANGRY,
            intensity = 0.7f,
            since = 0L,
            reason = MoodReason.EYE_POKE,
        )
        val text = MoodPromptFormatter.format(mood)

        assertTrue(text.contains("STATO ROBOT"))
        assertTrue(text.contains("angry (70%)"))
        assertTrue(text.contains("poke_occhi"))
        assertTrue(text.contains("Scuse sincere"))
    }
}
