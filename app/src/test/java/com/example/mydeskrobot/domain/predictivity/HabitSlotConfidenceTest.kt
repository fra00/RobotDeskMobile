package com.example.mydeskrobot.domain.predictivity

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitSlotConfidenceTest {

    @Test
    fun `confidence scales with hit count and caps at 0_90`() {
        assertEquals(0.129f, HabitSlotConfidence.confidenceForHitCount(1), 0.01f)
        assertEquals(0.386f, HabitSlotConfidence.confidenceForHitCount(3), 0.01f)
        assertEquals(0.90f, HabitSlotConfidence.confidenceForHitCount(7), 0.001f)
        assertEquals(0.90f, HabitSlotConfidence.confidenceForHitCount(10), 0.001f)
    }
}
