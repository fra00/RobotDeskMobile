package com.example.mydeskrobot.domain.predictivity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitSlotEligibilityTest {

    @Test
    fun `isEligibleForDeviation true at minimum thresholds`() {
        val slot = sampleSlot(hitCount = 3, confidence = 0.70f)
        assertTrue(HabitSlotEligibility.isEligibleForDeviation(slot))
    }

    @Test
    fun `isEligibleForDeviation false below hit count`() {
        val slot = sampleSlot(hitCount = 2, confidence = 0.75f)
        assertFalse(HabitSlotEligibility.isEligibleForDeviation(slot))
    }

    @Test
    fun `isEligibleForDeviation false below confidence`() {
        val slot = sampleSlot(hitCount = 5, confidence = 0.69f)
        assertFalse(HabitSlotEligibility.isEligibleForDeviation(slot))
    }

    private fun sampleSlot(hitCount: Int, confidence: Float) = HabitSlot(
        slotKey = "passeggiata_cane|510",
        canonicalLabel = "passeggiata_cane",
        displayLabel = "Passeggiata cane",
        typicalTimeMinutes = 510,
        hitCount = hitCount,
        confidence = confidence,
    )
}
