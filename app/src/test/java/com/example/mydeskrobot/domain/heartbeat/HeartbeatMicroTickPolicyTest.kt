package com.example.mydeskrobot.domain.heartbeat

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatMicroTickPolicyTest {

    @Test
    fun allows_bored_with_idle() {
        assertTrue(
            HeartbeatMicroTickPolicy.shouldRun(
                moodEmotion = RobotEmotion.BORED,
                idleMinutes = 16,
                presenceAllows = true,
                voiceSessionActive = true,
            ),
        )
    }

    @Test
    fun blocks_when_not_idle_enough() {
        assertFalse(
            HeartbeatMicroTickPolicy.shouldRun(
                moodEmotion = RobotEmotion.BORED,
                idleMinutes = 5,
                presenceAllows = true,
                voiceSessionActive = true,
            ),
        )
    }

    @Test
    fun blocks_without_presence() {
        assertFalse(
            HeartbeatMicroTickPolicy.shouldRun(
                moodEmotion = RobotEmotion.BORED,
                idleMinutes = 20,
                presenceAllows = false,
                voiceSessionActive = true,
            ),
        )
    }
}
