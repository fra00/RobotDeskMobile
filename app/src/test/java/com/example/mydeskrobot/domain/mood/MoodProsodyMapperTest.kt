package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodProsodyMapperTest {

    @Test
    fun `angry derive is audibly faster and slightly lower`() {
        val prosody = MoodProsodyMapper.derive(RobotEmotion.ANGRY, intensity = 0.8f)
        assertTrue(prosody.pitch < 0.95f)
        assertTrue(prosody.rate > 1.12f)
    }

    @Test
    fun `sad derive is audibly slower and lower`() {
        val prosody = MoodProsodyMapper.derive(RobotEmotion.SAD, intensity = 0.7f)
        assertTrue(prosody.pitch < 0.90f)
        assertTrue(prosody.rate < 0.90f)
    }

    @Test
    fun `happy derive is audibly higher and faster`() {
        val prosody = MoodProsodyMapper.derive(RobotEmotion.HAPPY, intensity = 0.7f)
        assertTrue(prosody.pitch > 1.12f)
        assertTrue(prosody.rate > 1.05f)
    }

    @Test
    fun `neutral ephemeral does not mask angry wellbeing`() {
        assertEquals(
            RobotEmotion.ANGRY,
            MoodProsodyMapper.resolveSpeechEmotion(
                wellbeing = RobotEmotion.ANGRY,
                ephemeral = RobotEmotion.NEUTRAL,
            ),
        )
    }

    @Test
    fun `sad ephemeral overrides neutral wellbeing`() {
        assertEquals(
            RobotEmotion.SAD,
            MoodProsodyMapper.resolveSpeechEmotion(
                wellbeing = RobotEmotion.NEUTRAL,
                ephemeral = RobotEmotion.SAD,
            ),
        )
    }

    @Test
    fun `forSpeech uses angry wellbeing when ephemeral is neutral`() {
        val mood = RobotMood.fromValence(
            valence = -0.25f,
            since = 1_000L,
            reason = MoodReason.EYE_POKE,
            forceEmotion = RobotEmotion.ANGRY,
            forceIntensity = 0.75f,
        )
        val ephemeral = EphemeralExpression(
            emotion = RobotEmotion.NEUTRAL,
            intensity = 0.45f,
            expiresAt = 100_000L,
        )
        val prosody = MoodProsodyMapper.forSpeech(mood, ephemeral, now = 10_000L)
        assertTrue(prosody.rate > 1.1f)
        assertTrue(prosody.pitch < 1f)
    }
}
