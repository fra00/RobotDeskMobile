package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodValenceMapperTest {

    @Test
    fun `high valence maps to happy`() {
        val derived = MoodValenceMapper.derive(0.30f, null)
        assertEquals(RobotEmotion.HAPPY, derived.emotion)
    }

    @Test
    fun `baseline maps to neutral`() {
        val derived = MoodValenceMapper.derive(0.1f, null)
        assertEquals(RobotEmotion.NEUTRAL, derived.emotion)
    }

    @Test
    fun `low valence maps to bored or sad`() {
        val bored = MoodValenceMapper.derive(-0.2f, MoodReason.IDLE_LONG)
        assertEquals(RobotEmotion.BORED, bored.emotion)

        val sad = MoodValenceMapper.derive(-0.35f, null)
        assertEquals(RobotEmotion.SAD, sad.emotion)
    }

    @Test
    fun `eye poke reason uses angry when valence low enough`() {
        val derived = MoodValenceMapper.derive(-0.2f, MoodReason.EYE_POKE)
        assertEquals(RobotEmotion.ANGRY, derived.emotion)
    }

    @Test
    fun `format valence shows sign`() {
        assertEquals("+0.35", MoodValenceMapper.formatValence(0.35f))
        assertEquals("-0.18", MoodValenceMapper.formatValence(-0.18f))
    }
}
