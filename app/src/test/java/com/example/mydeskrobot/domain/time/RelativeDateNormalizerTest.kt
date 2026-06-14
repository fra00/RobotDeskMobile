package com.example.mydeskrobot.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun normalize_keepsIlWeekdayAsRecurringRoutine() {
        val friday = calendarAt(2026, Calendar.JUNE, 12)

        assertEquals(
            "Il venerdì di solito l'utente va a cena",
            RelativeDateNormalizer.normalize(
                "Il venerdì di solito l'utente va a cena",
                friday.timeInMillis,
            ),
        )
        assertEquals(
            "Il venerdì esco con gli amici",
            RelativeDateNormalizer.normalize(
                "Il venerdì esco con gli amici",
                friday.timeInMillis,
            ),
        )
        assertEquals(
            "Di venerdì faccio questo",
            RelativeDateNormalizer.normalize("Di venerdì faccio questo", friday.timeInMillis),
        )
    }

    @Test
    fun normalize_resolvesBareWeekdayToDate() {
        val friday = calendarAt(2026, Calendar.JUNE, 12)

        assertEquals(
            "il 12 giugno 2026 fai questo",
            RelativeDateNormalizer.normalize("venerdì fai questo", friday.timeInMillis),
        )
    }

    @Test
    fun normalize_resolvesIlProssimoWeekdayToDate() {
        val monday = calendarAt(2026, Calendar.JUNE, 8)

        assertEquals(
            "Il prossimo il 12 giugno 2026 esco",
            RelativeDateNormalizer.normalize("Il prossimo venerdì esco", monday.timeInMillis),
        )
    }

    @Test
    fun isRecurringWeekdayReference_detectsArticleSubject() {
        val text = "Il venerdì esco con gli amici"
        val index = text.indexOf("venerdì")
        assertTrue(
            RelativeDateNormalizer.isRecurringWeekdayReference(
                text,
                index until index + "venerdì".length,
            ),
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
    fun normalize_keepsWeekdayRangeDalAl() {
        val monday = calendarAt(2026, Calendar.JUNE, 1)

        assertEquals(
            "Dal lunedì al sabato l'utente lavora",
            RelativeDateNormalizer.normalize(
                "Dal lunedì al sabato l'utente lavora",
                monday.timeInMillis,
            ),
        )
        assertEquals(
            "L'utente è in ufficio da lunedì a venerdì",
            RelativeDateNormalizer.normalize(
                "L'utente è in ufficio da lunedì a venerdì",
                monday.timeInMillis,
            ),
        )
        assertEquals(
            "Aperto tra il lunedì e il sabato",
            RelativeDateNormalizer.normalize(
                "Aperto tra il lunedì e il sabato",
                monday.timeInMillis,
            ),
        )
        assertEquals(
            "Fra martedì e giovedì ha riunione",
            RelativeDateNormalizer.normalize(
                "Fra martedì e giovedì ha riunione",
                monday.timeInMillis,
            ),
        )
        assertEquals(
            "Disponibile dal lunedì fino al sabato",
            RelativeDateNormalizer.normalize(
                "Disponibile dal lunedì fino al sabato",
                monday.timeInMillis,
            ),
        )
    }

    @Test
    fun normalize_resolvesBareAlWeekdayWithoutRangeContext() {
        val monday = calendarAt(2026, Calendar.JUNE, 1)

        assertEquals(
            "Vado al il 6 giugno 2026",
            RelativeDateNormalizer.normalize("Vado al sabato", monday.timeInMillis),
        )
    }

    @Test
    fun normalize_resolvesDaWeekdayWithoutRangeContinuation() {
        val monday = calendarAt(2026, Calendar.JUNE, 1)

        assertEquals(
            "Disponibile da il 1 giugno 2026",
            RelativeDateNormalizer.normalize("Disponibile da lunedì", monday.timeInMillis),
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
