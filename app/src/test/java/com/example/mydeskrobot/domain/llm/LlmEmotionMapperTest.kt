package com.example.mydeskrobot.domain.llm

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmEmotionMapperTest {

    @Test
    fun mapsItalianAndEnglish() {
        assertEquals(RobotEmotion.HAPPY, LlmEmotionMapper.fromLlmValue("felice"))
        assertEquals(RobotEmotion.SAD, LlmEmotionMapper.fromLlmValue("triste"))
        assertEquals(RobotEmotion.ANGRY, LlmEmotionMapper.fromLlmValue("angry"))
    }

    @Test
    fun unknownReturnsNull() {
        assertNull(LlmEmotionMapper.fromLlmValue("ecstatic"))
        assertNull(LlmEmotionMapper.fromLlmValue(null))
    }
}
