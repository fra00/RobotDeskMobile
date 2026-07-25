package com.example.mydeskrobot.domain.heartbeat

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleLookAroundEligibilityTest {

    private val now = 1_000_000_000L

    @Test
    fun `allows look-around when bored idle and cooldown clear`() {
        assertTrue(
            IdleLookAroundEligibility.shouldRun(
                microTickEnabled = true,
                voiceSessionActive = true,
                withinActiveWindow = true,
                isNightMode = false,
                robotContextSilent = false,
                presenceAllows = true,
                moodEmotion = RobotEmotion.BORED,
                idleMinutes = 16,
                lastLookAroundAtMs = null,
                intervalMinutes = 10,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `blocks when micro-tick disabled`() {
        assertFalse(
            IdleLookAroundEligibility.shouldRun(
                microTickEnabled = false,
                voiceSessionActive = true,
                withinActiveWindow = true,
                isNightMode = false,
                robotContextSilent = false,
                presenceAllows = true,
                moodEmotion = RobotEmotion.BORED,
                idleMinutes = 20,
                lastLookAroundAtMs = null,
                intervalMinutes = 10,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `blocks during cooldown even if idle enough`() {
        assertFalse(
            IdleLookAroundEligibility.shouldRun(
                microTickEnabled = true,
                voiceSessionActive = true,
                withinActiveWindow = true,
                isNightMode = false,
                robotContextSilent = false,
                presenceAllows = true,
                moodEmotion = RobotEmotion.BORED,
                idleMinutes = 20,
                lastLookAroundAtMs = now - 5 * 60_000L,
                intervalMinutes = 10,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `allows after cooldown elapsed`() {
        assertTrue(
            IdleLookAroundEligibility.shouldRun(
                microTickEnabled = true,
                voiceSessionActive = true,
                withinActiveWindow = true,
                isNightMode = false,
                robotContextSilent = false,
                presenceAllows = true,
                moodEmotion = RobotEmotion.BORED,
                idleMinutes = 20,
                lastLookAroundAtMs = now - 10 * 60_000L,
                intervalMinutes = 10,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `does not require speak budget — only silent gates`() {
        // Speak-cap is intentionally not a parameter; this documents that
        // proactive speak exhaustion must not block look-around.
        assertTrue(
            IdleLookAroundEligibility.shouldRun(
                microTickEnabled = true,
                voiceSessionActive = true,
                withinActiveWindow = true,
                isNightMode = false,
                robotContextSilent = false,
                presenceAllows = true,
                moodEmotion = RobotEmotion.DROWSY,
                idleMinutes = 15,
                lastLookAroundAtMs = null,
                intervalMinutes = 10,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `active window overnight span`() {
        assertTrue(IdleLookAroundEligibility.isWithinActiveWindow(22, 6, 23))
        assertTrue(IdleLookAroundEligibility.isWithinActiveWindow(22, 6, 5))
        assertFalse(IdleLookAroundEligibility.isWithinActiveWindow(22, 6, 12))
    }
}
