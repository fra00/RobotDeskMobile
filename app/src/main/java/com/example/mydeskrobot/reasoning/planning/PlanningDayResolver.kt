package com.example.mydeskrobot.reasoning.planning

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PlanningDayResolver {

    data class ResolvedDay(
        val dayKey: String,
        val dayOffset: Int,
    )

    fun resolve(userText: String, nowMillis: Long = System.currentTimeMillis()): ResolvedDay {
        val normalized = userText.trim().lowercase(Locale.ITALIAN)
        val offset = when {
            normalized.contains("dopodomani") -> 2
            normalized.contains("domani") -> 1
            else -> 0
        }
        val calendar = Calendar.getInstance(Locale.ITALY)
        calendar.timeInMillis = nowMillis
        calendar.add(Calendar.DAY_OF_YEAR, offset)
        val dayKey = dayKeyFormat.format(calendar.time)
        return ResolvedDay(dayKey = dayKey, dayOffset = offset)
    }

    fun formatDayLabel(dayKey: String): String {
        val parsed = dayKeyFormat.parse(dayKey) ?: return dayKey
        return dayLabelFormat.format(parsed)
    }

    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
    private val dayLabelFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)
}
