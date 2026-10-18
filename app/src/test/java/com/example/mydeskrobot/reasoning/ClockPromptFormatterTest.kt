package com.example.mydeskrobot.reasoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ClockPromptFormatterTest {

    private val rome = TimeZone.getTimeZone("Europe/Rome")

    @Test
    fun `section includes oggi domani and prossimo weekdays`() {
        // Monday 2026-10-19 10:15 Rome
        val cal = Calendar.getInstance(rome).apply {
            set(2026, Calendar.OCTOBER, 19, 10, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val text = ClockPromptFormatter.buildSection(cal.timeInMillis, rome)

        assertTrue(text.startsWith("ORARIO"))
        assertTrue(text.contains("Oggi:"))
        assertTrue(text.contains("2026-10-19"))
        assertTrue(text.contains("Domani:"))
        assertTrue(text.contains("2026-10-20"))
        assertTrue(text.contains("prossimo martedì:"))
        assertTrue(text.contains("2026-10-20")) // next Tuesday from Monday = tomorrow
        assertTrue(text.contains("prossimo lunedì:"))
        assertTrue(text.contains("2026-10-26")) // next Monday excluding today
        assertTrue(text.contains("VERIFY_CLAIMS"))
    }

    @Test
    fun `nextWeekdayExcludingToday skips today`() {
        val monday = Calendar.getInstance(rome).apply {
            set(2026, Calendar.OCTOBER, 19, 12, 0, 0) // Monday
            set(Calendar.MILLISECOND, 0)
        }
        val nextMonday = ClockPromptFormatter.nextWeekdayExcludingToday(monday, Calendar.MONDAY)
        assertEquals(2026, nextMonday.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, nextMonday.get(Calendar.MONTH))
        assertEquals(26, nextMonday.get(Calendar.DAY_OF_MONTH))
    }
}
