package com.example.mydeskrobot.domain.time

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Replaces Italian relative day references (oggi, domani, weekday names) with absolute
 * calendar dates so stored text does not become stale the next day.
 */
object RelativeDateNormalizer {

    private val italianLocale = Locale.ITALIAN

    private val datePhraseFormat = SimpleDateFormat("d MMMM yyyy", italianLocale)

    private val afterTomorrowPattern =
        Regex("""(?i)\b(?:dopodomani|dopo\s+domani)\b""")
    private val tomorrowPattern = Regex("""(?i)\bdomani\b""")
    private val todayPattern = Regex("""(?i)\boggi\b""")

    private val weekdayTokens = listOf(
        WeekdayToken("domenica", Calendar.SUNDAY),
        WeekdayToken("lunedi", Calendar.MONDAY),
        WeekdayToken("lunedì", Calendar.MONDAY),
        WeekdayToken("martedi", Calendar.TUESDAY),
        WeekdayToken("martedì", Calendar.TUESDAY),
        WeekdayToken("mercoledi", Calendar.WEDNESDAY),
        WeekdayToken("mercoledì", Calendar.WEDNESDAY),
        WeekdayToken("giovedi", Calendar.THURSDAY),
        WeekdayToken("giovedì", Calendar.THURSDAY),
        WeekdayToken("venerdi", Calendar.FRIDAY),
        WeekdayToken("venerdì", Calendar.FRIDAY),
        WeekdayToken("sabato", Calendar.SATURDAY),
    )

    fun normalize(
        text: String,
        referenceEpochMs: Long = System.currentTimeMillis(),
    ): String {
        if (text.isBlank()) return text

        val reference = Calendar.getInstance(italianLocale).apply {
            timeInMillis = referenceEpochMs
        }

        var result = text
        result = replaceRelativeDay(result, afterTomorrowPattern, reference, dayOffset = 2)
        result = replaceRelativeDay(result, tomorrowPattern, reference, dayOffset = 1)
        result = replaceRelativeDay(result, todayPattern, reference, dayOffset = 0)
        result = replaceWeekdays(result, reference)
        return result
    }

    private fun replaceRelativeDay(
        text: String,
        pattern: Regex,
        reference: Calendar,
        dayOffset: Int,
    ): String {
        val targetDate = (reference.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        val phrase = formatItalianDatePhrase(targetDate)
        return pattern.replace(text) { phrase }
    }

    private fun replaceWeekdays(text: String, reference: Calendar): String {
        var result = text
        weekdayTokens.forEach { token ->
            val pattern = weekdayPattern(token.label)
            result = pattern.replace(result) { match ->
                val targetDate = resolveWeekdayDate(reference, token.calendarDay)
                formatItalianDatePhrase(targetDate)
            }
        }
        return result
    }

    /**
     * Word boundaries via Unicode letters — Java \b does not treat accented chars (e.g. ì) as word chars.
     */
    private fun weekdayPattern(label: String): Regex =
        Regex("""(?i)(?<![\p{L}])(?<!ogni\s)${Regex.escape(label)}(?![\p{L}])""")

    private fun resolveWeekdayDate(reference: Calendar, targetDayOfWeek: Int): Calendar {
        val result = (reference.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentDay = result.get(Calendar.DAY_OF_WEEK)
        var daysUntil = (targetDayOfWeek - currentDay + 7) % 7
        result.add(Calendar.DAY_OF_YEAR, daysUntil)
        return result
    }

    private fun formatItalianDatePhrase(calendar: Calendar): String {
        val formatted = datePhraseFormat.format(calendar.time)
        return "il $formatted"
    }

    private data class WeekdayToken(
        val label: String,
        val calendarDay: Int,
    )
}
