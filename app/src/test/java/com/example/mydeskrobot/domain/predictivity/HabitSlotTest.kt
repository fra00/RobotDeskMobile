package com.example.mydeskrobot.domain.predictivity

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitSlotTest {

    @Test
    fun `withHit sets typical time on first hit`() {
        val slot = baseSlot()
        val updated = slot.withHit(episodeTimeMinutes = 510, dayKey = "2026-06-10", rawLabel = "passeggiata cane")

        assertEquals(1, updated.hitCount)
        assertEquals(510, updated.typicalTimeMinutes)
        assertEquals("2026-06-10", updated.lastHitDayKey)
        assertEquals(setOf("passeggiata cane"), updated.rawLabels)
    }

    @Test
    fun `withHit rolling average typical time across hits`() {
        val afterFirst = baseSlot().withHit(510, "2026-06-10", "passeggiata cane")
        val afterSecond = afterFirst.withHit(520, "2026-06-11", "passeggiata con il cane")

        assertEquals(2, afterSecond.hitCount)
        assertEquals(515, afterSecond.typicalTimeMinutes)
        assertEquals(setOf("passeggiata cane", "passeggiata con il cane"), afterSecond.rawLabels)
    }

    private fun baseSlot() = HabitSlot(
        slotKey = "passeggiata_cane|510",
        canonicalLabel = "passeggiata_cane",
        displayLabel = "Passeggiata cane",
        typicalTimeMinutes = 0,
    )
}
