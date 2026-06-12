package com.example.mydeskrobot.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class RelativeDateNormalizerTest {

    @Test
    fun normalize_replacesDomaniWithAbsoluteDate() {
        val reference = calendarAt(2026, Calendar.JUNE, 2)

        val result = RelativeDateNormalizer.normalize(
            text = "Consegnare il documento domani",
            referenceEpochMs = reference.timeInMillis,
        )

        assertEquals("Consegnare il documento il 3 giugno 2026", result)
    }

    @Test
    fun normalize_replacesOggiAndDopodomani() {
        val reference = calendarAt(2026, Calendar.JUNE, 2)

        assertEquals(
            "Task il 2 giugno 2026",
            RelativeDateNormalizer.normalize("Task oggi", reference.timeInMillis),
        )
        assertEquals(
            "Viaggio il 4 giugno 2026",
            RelativeDateNormalizer.normalize("Viaggio dopodomani", reference.timeInMillis),
        )
        assertEquals(
            "Viaggio il 4 giugno 2026",
            RelativeDateNormalizer.normalize("Viaggio dopo domani", reference.timeInMillis),
        )
    }

    @Test
    fun normalize_replacesWeekdayUnlessRoutine() {
        val tuesday = calendarAt(2026, Calendar.JUNE, 2)

        assertEquals(
            "Riunione con Marco il 2 giugno 2026",
            RelativeDateNormalizer.normalize("Riunione con Marco martedì", tuesday.timeInMillis),
        )
        assertEquals(
            "L'utente va in palestra ogni martedì",
            RelativeDateNormalizer.normalize(
                "L'utente va in palestra ogni martedì",
                tuesday.timeInMillis,
            ),
        )
    }

    @Test
    fun normalize_nextWeekdayWhenTodayDiffers() {
        val monday = calendarAt(2026, Calendar.JUNE, 1)

        assertEquals(
            "Chiamare il idraulico il 2 giugno 2026",
            RelativeDateNormalizer.normalize("Chiamare il idraulico martedì", monday.timeInMillis),
        )
    }

    private fun calendarAt(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance(Locale.ITALIAN).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
