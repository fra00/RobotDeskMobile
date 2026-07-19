package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmEmotionValenceMapperTest {

    @Test
    fun `sad and angry have negative impact`() {
        assertTrue(LlmEmotionValenceMapper.hasNegativeValenceImpact(RobotEmotion.SAD))
        assertTrue(LlmEmotionValenceMapper.hasNegativeValenceImpact(RobotEmotion.ANGRY))
        assertTrue(LlmEmotionValenceMapper.hasNegativeValenceImpact(RobotEmotion.CONFUSED))
    }

    @Test
    fun `neutral and thinking have no valence delta`() {
        assertEquals(null, LlmEmotionValenceMapper.valenceDelta(RobotEmotion.NEUTRAL))
        assertFalse(LlmEmotionValenceMapper.hasNegativeValenceImpact(RobotEmotion.THINKING))
    }

    @Test
    fun `happy has positive delta`() {
        assertEquals(MoodValenceConfig.LLM_HAPPY_DELTA, LlmEmotionValenceMapper.valenceDelta(RobotEmotion.HAPPY))
    }
}
