package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodReplyStyleResolverTest {

    @Test
    fun `sad emotion resolves terse`() {
        val mood = RobotMood.fromValence(
            valence = -0.3f,
            since = 0L,
            reason = MoodReason.LLM_EXPRESSION,
            forceEmotion = RobotEmotion.SAD,
            forceIntensity = 0.5f,
        )
        assertEquals(MoodReplyStyle.TERSE, MoodReplyStyleResolver.resolve(mood))
    }

    @Test
    fun `happy emotion resolves warm`() {
        val mood = RobotMood.fromValence(
            valence = 0.35f,
            since = 0L,
            reason = MoodReason.LLM_EXPRESSION,
            forceEmotion = RobotEmotion.HAPPY,
            forceIntensity = 0.6f,
        )
        assertEquals(MoodReplyStyle.WARM, MoodReplyStyleResolver.resolve(mood))
    }

    @Test
    fun `neutral valence resolves normal`() {
        val mood = RobotMood.fromValence(
            valence = 0.1f,
            since = 0L,
            reason = null,
        )
        assertEquals(MoodReplyStyle.NORMAL, MoodReplyStyleResolver.resolve(mood))
    }

    @Test
    fun `formatter includes style block`() {
        val mood = RobotMood.fromValence(
            valence = -0.25f,
            since = 0L,
            reason = null,
            forceEmotion = RobotEmotion.ANGRY,
            forceIntensity = 0.7f,
        )
        val text = MoodPromptFormatter.format(mood)
        assertTrue(text.contains("STILE RISPOSTA"))
        assertTrue(text.contains("sintetico"))
    }
}
