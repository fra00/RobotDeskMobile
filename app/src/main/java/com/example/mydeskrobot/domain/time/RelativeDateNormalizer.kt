package com.example.mydeskrobot.domain.time

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Replaces Italian relative day references (oggi, domani, bare weekday names) with absolute
 * calendar dates so stored text does not become stale the next day.
 *
 * Weekday rules (Italian):
 * - Recurring subject → keep weekday name: "il/la venerdì", "di venerdì", "ogni martedì",
 *   "venerdì di solito", ranges "dal lunedì al sabato", "tra martedì e giovedì", …
 * - One-off → resolve to date: bare "venerdì", "martedì prossimo", "il prossimo venerdì"
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

    private val weekdayNameAlternation: String by lazy {
        weekdayTokens
            .map { Regex.escape(it.label) }
            .distinct()
            .joinToString("|")
    }

    private val optionalArticle = """(?:il\s+|la\s+)?"""

    /** After weekday: " al sabato", " fino al venerdì", " a venerdì". */
    private val rangeContinuationSuffix: Regex by lazy {
        Regex(
            """(?i)^\s+(?:fino\s+)?(?:al|alla|a)\s+$optionalArticle(?:$weekdayNameAlternation)(?![\p{L}])""",
        )
    }

    /** After weekday: " e sabato", " e il sabato". */
    private val rangeAndSuffix: Regex by lazy {
        Regex(
            """(?i)^\s+e\s+$optionalArticle(?:$weekdayNameAlternation)(?![\p{L}])""",
        )
    }

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
                if (isRecurringWeekdayReference(result, match.range)) {
                    match.value
                } else {
                    val targetDate = resolveWeekdayDate(reference, token.calendarDay)
                    formatItalianDatePhrase(targetDate)
                }
            }
        }
        return result
    }

    /**
     * Word boundaries via Unicode letters — Java \b does not treat accented chars (e.g. ì) as word chars.
     */
    private fun weekdayPattern(label: String): Regex =
        Regex("""(?i)(?<![\p{L}])${Regex.escape(label)}(?![\p{L}])""")

    /**
     * True when the weekday names a recurring habit (day as subject), not a single calendar day.
     */
    internal fun isRecurringWeekdayReference(text: String, matchRange: IntRange): Boolean {
        val start = matchRange.first
        val end = matchRange.last + 1
        val before = text.substring(0, start)
        val after = text.substring(end)

        if (matchesAtStart(ONE_OFF_WEEKDAY_SUFFIX, after)) return false

        if (matchesAtStart(RECURRING_WEEKDAY_SUFFIX, after)) return true

        if (RECURRING_WEEKDAY_PREFIXES.any { it.containsMatchIn(before) }) return true

        if (isWeekdayRangeReference(before, after)) return true

        return false
    }

    /**
     * Weekday ranges and span prepositions: "dal lunedì al sabato", "da martedì a venerdì",
     * "tra il lunedì e il sabato", "fino al venerdì" (when another weekday precedes).
     */
    private fun isWeekdayRangeReference(before: String, after: String): Boolean {
        if (matchesAtStart(rangeContinuationSuffix, after)) return true
        if (matchesAtStart(rangeAndSuffix, after)) return true

        if (RANGE_FROM_PREFIX.matches(before)) return true

        if (RANGE_TRA_FRA_PREFIX.matches(before) && matchesAtStart(rangeAndSuffix, after)) return true

        if (Regex("""(?i)da\s$""").containsMatchIn(before)) {
            if (matchesAtStart(rangeContinuationSuffix, after)) return true
            if (matchesAtStart(Regex("""(?i)^\s+(?:in\s+poi|in\s+avanti)\b"""), after)) return true
        }

        if (RANGE_TO_PREFIXES.any { it.containsMatchIn(before) } && containsWeekdayToken(before)) {
            return true
        }

        return false
    }

    private fun matchesAtStart(pattern: Regex, text: String): Boolean =
        pattern.matchAt(text, 0) != null

    private fun containsWeekdayToken(text: String): Boolean =
        weekdayTokens.any { token -> weekdayPattern(token.label).containsMatchIn(text) }

    /** e.g. "venerdì prossimo", "martedì scorso" → still resolve to a date. */
    private val ONE_OFF_WEEKDAY_SUFFIX =
        Regex("""(?i)^\s+(prossim[oa]|scors[oa]|che\s+viene|quest[oa])\b""")

    /** e.g. "venerdì di solito esco" without article. */
    private val RECURRING_WEEKDAY_SUFFIX =
        Regex("""(?i)^\s+di\s+solito\b""")

    /**
     * Prefixes that mark the weekday as recurring subject: "il venerdì", "di venerdì", "ogni martedì".
     */
    private val RECURRING_WEEKDAY_PREFIXES = listOf(
        Regex("""(?i)ogni\s$"""),
        Regex("""(?i)il\s$"""),
        Regex("""(?i)la\s$"""),
        Regex("""(?i)di\s$"""),
        Regex("""(?i)i\s$"""),
        Regex("""(?i)tutti\s+i\s$"""),
        Regex("""(?i)tutti\s+gl'?\s*$"""),
    )

    private val RANGE_FROM_PREFIX = Regex("""(?i)(?:dal|dalla)\s$""")

    private val RANGE_TRA_FRA_PREFIX = Regex("""(?i)(?:tra|fra)\s$""")

    /** Second (or last) weekday in a range: "… lunedì al ", "… martedì e ". */
    private val RANGE_TO_PREFIXES = listOf(
        Regex("""(?i)al\s$"""),
        Regex("""(?i)alla\s$"""),
        Regex("""(?i)a\s$"""),
        Regex("""(?i)e\s$"""),
        Regex("""(?i)fino\s+al\s$"""),
        Regex("""(?i)fino\s+alla\s$"""),
        Regex("""(?i)fino\s+a\s$"""),
    )

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
