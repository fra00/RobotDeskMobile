package com.example.mydeskrobot.reasoning.planning

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class PlanningDayResolverTest {

    @Test
    fun `resolve defaults to today`() {
        val now = calendarOf(2026, Calendar.JUNE, 2, 10, 0).timeInMillis
        val resolved = PlanningDayResolver.resolve("cosa devo fare oggi", now)
        assertEquals("2026-06-02", resolved.dayKey)
        assertEquals(0, resolved.dayOffset)
    }

    @Test
    fun `resolve domani adds one day`() {
        val now = calendarOf(2026, Calendar.JUNE, 2, 10, 0).timeInMillis
        val resolved = PlanningDayResolver.resolve("cosa devo fare domani", now)
        assertEquals("2026-06-03", resolved.dayKey)
        assertEquals(1, resolved.dayOffset)
    }

    @Test
    fun `resolve dopodomani adds two days`() {
        val now = calendarOf(2026, Calendar.JUNE, 2, 10, 0).timeInMillis
        val resolved = PlanningDayResolver.resolve("impegni dopodomani", now)
        assertEquals("2026-06-04", resolved.dayKey)
        assertEquals(2, resolved.dayOffset)
    }

    private fun calendarOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar {
        return Calendar.getInstance(Locale.ITALY).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
