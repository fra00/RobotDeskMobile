package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.domain.mood.RobotMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodUiStateMapperTest {

    @Test
    fun mapsWellbeingAndEphemeralSnapshot() {
        val mood = RobotMood.fromValence(
            valence = 0.3f,
            since = 1_000L,
            reason = MoodReason.LLM_EXPRESSION,
            forceEmotion = RobotEmotion.HAPPY,
            forceIntensity = 0.7f,
        )
        val ephemeral = com.example.mydeskrobot.domain.mood.EphemeralExpression(
            emotion = RobotEmotion.ANGRY,
            intensity = 0.75f,
            expiresAt = 31_000L,
        )

        val ui = MoodUiStateMapper.from(
            mood = mood,
            ephemeral = ephemeral,
            displayEmotion = RobotEmotion.ANGRY,
            displayIntensity = 0.75f,
            idleMinutes = 12L,
            now = 10_000L,
        )

        assertEquals(0.3f, ui.valence, 0.001f)
        assertEquals(RobotEmotion.HAPPY, ui.baseEmotion)
        assertEquals(RobotEmotion.ANGRY, ui.displayEmotion)
        assertEquals(RobotEmotion.ANGRY, ui.ephemeralEmotion)
        assertEquals(21L, ui.ephemeralRemainingSeconds)
        assertEquals(12L, ui.idleMinutes)
        assertTrue(ui.promptSnapshot.contains("STATO ROBOT"))
        assertTrue(ui.promptSnapshot.contains("PRIORITÀ FACCIA"))
        assertTrue(ui.promptSnapshot.contains("angry"))
    }
}
