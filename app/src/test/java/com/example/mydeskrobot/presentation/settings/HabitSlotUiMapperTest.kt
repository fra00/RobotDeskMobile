package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.proactive.ProactivityConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitSlotUiMapperTest {

    @Test
    fun `formatMinutesSinceMidnight wraps and pads`() {
        assertEquals("08:30", HabitSlotUiMapper.formatMinutesSinceMidnight(8 * 60 + 30))
        assertEquals("00:15", HabitSlotUiMapper.formatMinutesSinceMidnight(15))
        assertEquals("23:50", HabitSlotUiMapper.formatMinutesSinceMidnight(-10))
    }

    @Test
    fun `hitsNeededForEligibility is about six days at defaults`() {
        assertEquals(6, HabitSlotUiMapper.hitsNeededForEligibility())
    }

    @Test
    fun `fromDomain marks immature slot`() {
        val ui = HabitSlotUiMapper.fromDomain(
            HabitSlot(
                slotKey = "walk|480",
                canonicalLabel = "walk",
                displayLabel = "Passeggiata",
                typicalTimeMinutes = 8 * 60 + 30,
                hitCount = 2,
                confidence = 2 * (0.90f / ProactivityConstants.PREDICTIVITY_PROMOTE_HIT_COUNT),
            ),
        )
        assertEquals("Passeggiata", ui.displayLabel)
        assertEquals("08:30", ui.typicalTimeLabel)
        assertFalse(ui.eligible)
        assertTrue(ui.statusLabel.contains("apprendimento", ignoreCase = true))
    }

    @Test
    fun `fromDomain marks eligible slot`() {
        val hits = HabitSlotUiMapper.hitsNeededForEligibility()
        val ui = HabitSlotUiMapper.fromDomain(
            HabitSlot(
                slotKey = "walk|480",
                canonicalLabel = "walk",
                displayLabel = "Passeggiata",
                typicalTimeMinutes = 8 * 60 + 30,
                hitCount = hits,
                confidence = hits * (0.90f / ProactivityConstants.PREDICTIVITY_PROMOTE_HIT_COUNT),
            ),
        )
        assertTrue(ui.eligible)
        assertTrue(ui.statusLabel.contains("Pronto", ignoreCase = true))
    }
}
