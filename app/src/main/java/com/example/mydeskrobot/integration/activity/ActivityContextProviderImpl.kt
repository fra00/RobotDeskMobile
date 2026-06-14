package com.example.mydeskrobot.integration.activity

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.reasoning.ActivityContextProvider
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityContextProviderImpl(
    private val activityLogRepository: ActivityLogRepository,
) : ActivityContextProvider {

    override suspend fun buildPromptSection(): String {
        val summary = activityLogRepository.getHabitSummary()?.summaryText
        val recent = activityLogRepository.getRecentForContext(maxEvents = 8, daysBack = 2)
        if (summary.isNullOrBlank() && recent.isEmpty()) return ""

        return buildString {
            if (!summary.isNullOrBlank()) {
                append("PROFILO ABITUDINI (ultimi 7 giorni):\n")
                append(summary.trim())
                append('\n')
            }
            if (recent.isNotEmpty()) {
                append("ATTIVITÀ RECENTI:\n")
                val timeFormat = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)
                recent.forEach { event ->
                    append("- ${timeFormat.format(event.timestampMs)} ${event.label}\n")
                }
            }
        }.trim()
    }

    override suspend fun buildHeartbeatSection(): String {
        val summary = activityLogRepository.getHabitSummary()?.summaryText?.take(400)
        val recent = activityLogRepository.getRecentForContext(maxEvents = 4, daysBack = 1)
        if (summary.isNullOrBlank() && recent.isEmpty()) return ""

        return buildString {
            if (!summary.isNullOrBlank()) {
                append("Abitudini: ${summary.trim()}\n")
            }
            if (recent.isNotEmpty()) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALY)
                val labels = recent.joinToString(", ") { "${timeFormat.format(it.timestampMs)} ${it.label}" }
                append("Oggi: $labels")
            }
        }.trim()
    }
}
