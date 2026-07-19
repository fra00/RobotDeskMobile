package com.example.mydeskrobot.data.predictivity

import com.example.mydeskrobot.domain.predictivity.HabitSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HabitSlotCodecTest {

    @Test
    fun `encode decode round trip`() {
        val slot = HabitSlot(
            slotKey = "passeggiata_cane|510",
            canonicalLabel = "passeggiata_cane",
            displayLabel = "Passeggiata cane",
            typicalTimeMinutes = 510,
            hitCount = 5,
            confidence = 0.75f,
            lastHitDayKey = "2026-06-10",
            rawLabels = setOf("passeggiata cane"),
        )

        val encoded = HabitSlotCodec.encode(slot)
        assertNotNull(encoded)
        val decoded = HabitSlotCodec.decode(encoded)
        assertNotNull("encoded=$encoded", decoded)
        assertEquals(slot.slotKey, decoded!!.slotKey)
        assertEquals(slot.hitCount, decoded.hitCount)
    }
}
