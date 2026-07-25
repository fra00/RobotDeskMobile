package com.example.mydeskrobot.domain.mood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IdleBoredomControllerTest {

    private val config = IdleBoredomConfig(
        lookAroundReliefMinutes = 5,
        distractionMinutes = 8,
    )
    private val base = 1_000_000_000L

    @Test
    fun `look-around starts relief and suppresses idle boredom`() {
        val controller = IdleBoredomController(config, Random(0))
        assertTrue(controller.onLookAroundCompleted(base))
        assertTrue(controller.isSuppressingIdleBoredom())
        assertTrue(controller.blocksLookAround())
        assertFalse(controller.onLookAroundCompleted(base + 1_000))
    }

    @Test
    fun `after relief still idle starts distraction`() {
        val controller = IdleBoredomController(config, Random(1))
        controller.onLookAroundCompleted(base)
        val atExpiry = base + 5 * 60_000L
        val result = controller.tick(
            nowMs = atExpiry,
            stillIdleEligible = true,
            allowNewDistraction = true,
        )
        assertTrue(result is IdleBoredomTickResult.StartedDistraction)
        val kind = (result as IdleBoredomTickResult.StartedDistraction).kind
        assertEquals(kind, controller.currentDistractionKind())
        assertTrue(controller.isSuppressingIdleBoredom())
    }

    @Test
    fun `after relief not idle clears without distraction`() {
        val controller = IdleBoredomController(config, Random(0))
        controller.onLookAroundCompleted(base)
        val result = controller.tick(
            nowMs = base + 5 * 60_000L,
            stillIdleEligible = false,
            allowNewDistraction = true,
        )
        assertEquals(IdleBoredomTickResult.Cleared, result)
        assertFalse(controller.isSuppressingIdleBoredom())
        assertNull(controller.currentDistractionKind())
    }

    @Test
    fun `night blocks new distraction after relief`() {
        val controller = IdleBoredomController(config, Random(0))
        controller.onLookAroundCompleted(base)
        val result = controller.tick(
            nowMs = base + 5 * 60_000L,
            stillIdleEligible = true,
            allowNewDistraction = false,
        )
        assertEquals(IdleBoredomTickResult.Cleared, result)
    }

    @Test
    fun `distraction ends after configured duration`() {
        val controller = IdleBoredomController(config, Random(2))
        controller.onLookAroundCompleted(base)
        controller.tick(base + 5 * 60_000L, stillIdleEligible = true, allowNewDistraction = true)
        val end = base + 5 * 60_000L + 8 * 60_000L
        val result = controller.tick(end, stillIdleEligible = true, allowNewDistraction = true)
        assertEquals(IdleBoredomTickResult.DistractionEnded, result)
        assertFalse(controller.isSuppressingIdleBoredom())
        assertNull(controller.currentDistractionKind())
    }

    @Test
    fun `interrupt mid distraction clears phase`() {
        val controller = IdleBoredomController(config, Random(3))
        controller.onLookAroundCompleted(base)
        controller.tick(base + 5 * 60_000L, stillIdleEligible = true, allowNewDistraction = true)
        assertEquals(IdleBoredomTickResult.DistractionEnded, controller.interrupt())
        assertFalse(controller.isSuppressingIdleBoredom())
    }

    @Test
    fun `interrupt mid relief clears without distraction ended`() {
        val controller = IdleBoredomController(config, Random(0))
        controller.onLookAroundCompleted(base)
        assertEquals(IdleBoredomTickResult.Cleared, controller.interrupt())
        assertFalse(controller.isSuppressingIdleBoredom())
    }
}
