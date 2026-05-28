package com.example.mydeskrobot.ui.eyes

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertNotNull
import org.junit.Test

class RobotEmotionEyesTest {

    @Test
    fun everyEmotionHasEyeSpec() {
        RobotEmotion.entries.forEach { emotion ->
            assertNotNull("Missing eye spec for $emotion", RobotEmotionEyes.specFor(emotion))
        }
    }
}
