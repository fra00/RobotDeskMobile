package com.example.mydeskrobot.domain.time

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NightModeHelperTest {

    @Test
    fun defaultNightWindow_coversMidnightToSixAm() {
        val config = NightModeConfig(startHour = 0, endHour = 6)

        assertTrue(isNightAt(config, hour = 0))
        assertTrue(isNightAt(config, hour = 3))
        assertTrue(isNightAt(config, hour = 5))
        assertFalse(isNightAt(config, hour = 6))
        assertFalse(isNightAt(config, hour = 12))
        assertFalse(isNightAt(config, hour = 23))
    }

    @Test
    fun wrappedWindow_coversLateEveningAndEarlyMorning() {
        val config = NightModeConfig(startHour = 22, endHour = 6)

        assertFalse(isNightAt(config, hour = 21))
        assertTrue(isNightAt(config, hour = 22))
        assertTrue(isNightAt(config, hour = 2))
        assertFalse(isNightAt(config, hour = 6))
        assertFalse(isNightAt(config, hour = 12))
    }

    private fun isNightAt(config: NightModeConfig, hour: Int): Boolean {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
        }
        return NightModeHelper.isNightMode(config, calendar)
    }
}
