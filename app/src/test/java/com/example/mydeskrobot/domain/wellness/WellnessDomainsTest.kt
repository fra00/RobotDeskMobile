package com.example.mydeskrobot.domain.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessDomainsTest {

    @Test
    fun `care catalog has five domains without spatial`() {
        assertEquals(5, WellnessDomains.ALL.size)
        assertTrue(WellnessDomains.ORDER in WellnessDomains.ALL)
        assertFalse("spatial" in WellnessDomains.ALL)
        assertEquals(
            setOf(
                WellnessDomains.MEALS,
                WellnessDomains.MOVEMENT,
                WellnessDomains.WORKLOAD,
                WellnessDomains.SOCIAL,
                WellnessDomains.ORDER,
            ),
            WellnessDomains.ALL,
        )
    }

    @Test
    fun `display names cover all care domains`() {
        WellnessDomains.ALL.forEach { id ->
            assertTrue(WellnessDomains.DISPLAY_NAMES.containsKey(id))
        }
    }
}
