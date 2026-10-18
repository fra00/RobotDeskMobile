package com.example.mydeskrobot.reasoning

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Per-turn clock block appended at the end of the dialog system prompt (with memory/mood).
 * Keeps {{CURRENT_DATETIME}} out of the static prefix for KV-cache friendliness and
 * precomputes relative days so the LLM does not do date arithmetic.
 */
object ClockPromptFormatter {

    private val localeIt = Locale.ITALIAN

    private val weekdayNames = listOf(
        "domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato",
    )

    fun buildSection(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val cal = Calendar.getInstance(timeZone, localeIt).apply { timeInMillis = nowMillis }
        val longFmt = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", localeIt).apply { this.timeZone = timeZone }
        val isoFmt = SimpleDateFormat("yyyy-MM-dd", localeIt).apply { this.timeZone = timeZone }
        val dayFmt = SimpleDateFormat("EEEE", localeIt).apply { this.timeZone = timeZone }

        fun label(c: Calendar): String {
            val iso = isoFmt.format(c.time)
            val day = dayFmt.format(c.time)
            return "$day $iso"
        }

        val oggi = cal.clone() as Calendar
        val ieri = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
        val domani = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val dopodomani = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 2) }

        return buildString {
            appendLine("ORARIO (autoritativo — usa queste date; non calcolare a mente):")
            appendLine("- Adesso: ${longFmt.format(Date(nowMillis))}")
            appendLine("- Oggi: ${label(oggi)}")
            appendLine("- Ieri: ${label(ieri)}")
            appendLine("- Domani: ${label(domani)}")
            appendLine("- Dopodomani: ${label(dopodomani)}")
            appendLine("- Prossimi giorni della settimana (escluso oggi se coincide):")
            for (name in weekdayNames) {
                val targetDow = weekdayNameToCalendarDow(name)
                val next = nextWeekdayExcludingToday(cal, targetDow)
                appendLine("  - prossimo $name: ${label(next)}")
            }
            appendLine("Per note/TODO/reminder: copia ISO o giorno da questo blocco. VERIFY_CLAIMS: correggi la data utente usando Adesso/Oggi qui.")
        }.trimEnd()
    }

    /** Next occurrence of [targetDow]; if today is that weekday, jump +7 days. */
    internal fun nextWeekdayExcludingToday(from: Calendar, targetDow: Int): Calendar {
        val c = from.clone() as Calendar
        val today = c.get(Calendar.DAY_OF_WEEK)
        var delta = (targetDow - today + 7) % 7
        if (delta == 0) delta = 7
        c.add(Calendar.DAY_OF_MONTH, delta)
        return c
    }

    private fun weekdayNameToCalendarDow(italianName: String): Int = when (italianName) {
        "domenica" -> Calendar.SUNDAY
        "lunedì" -> Calendar.MONDAY
        "martedì" -> Calendar.TUESDAY
        "mercoledì" -> Calendar.WEDNESDAY
        "giovedì" -> Calendar.THURSDAY
        "venerdì" -> Calendar.FRIDAY
        "sabato" -> Calendar.SATURDAY
        else -> error("Unknown weekday: $italianName")
    }
}
