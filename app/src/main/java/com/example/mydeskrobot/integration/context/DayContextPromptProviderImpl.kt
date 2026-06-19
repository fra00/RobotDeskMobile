package com.example.mydeskrobot.integration.context

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.domain.activitylog.ActivityLogEntry
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.domain.list.ListItemType
import com.example.mydeskrobot.reasoning.DayContextProvider
import com.example.mydeskrobot.reasoning.memory.MemoryIntentDetector
import com.example.mydeskrobot.reasoning.memory.MemoryRetrievalProfile
import com.example.mydeskrobot.reasoning.planning.PlanningDayResolver
import java.text.SimpleDateFormat
import java.util.Locale

class DayContextPromptProviderImpl(
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val listItemRepository: ListItemRepository,
    private val activityLogRepository: ActivityLogRepository,
) : DayContextProvider {

    override suspend fun buildContextSection(userText: String): String {
        val detection = MemoryIntentDetector.detect(userText)
        if (!detection.includes(MemoryRetrievalProfile.PLAN)) {
            return ""
        }

        val resolvedDay = PlanningDayResolver.resolve(userText)
        val (startOfDay, endOfDay) = ActivityLogRepository.dayBoundsForDayKey(resolvedDay.dayKey)
        val reminders = scheduledTaskRepository.listPendingForDay(startOfDay, endOfDay)
        val todos = listItemRepository.list(type = ListItemType.TODO, checked = false, limit = 8)
        val notes = listItemRepository.list(type = ListItemType.NOTE, limit = 5)
        val upcomingEpisodes = activityLogRepository.getUpcomingForDay(resolvedDay.dayKey)

        if (reminders.isEmpty() && todos.isEmpty() && notes.isEmpty() && upcomingEpisodes.isEmpty()) {
            return ""
        }

        val dayLabel = PlanningDayResolver.formatDayLabel(resolvedDay.dayKey)
        return buildString {
            val hasDayContext = reminders.isNotEmpty() || todos.isNotEmpty() || notes.isNotEmpty()
            if (hasDayContext) {
                appendLine("CONTESTO GIORNO ($dayLabel):")
                reminders.forEach { task ->
                    val time = scheduledTaskRepository.formatScheduledTime(task.triggerAtMillis)
                    appendLine("- $time ${task.message}")
                }
                todos.forEach { item ->
                    appendLine("- TODO: ${item.text}")
                }
                notes.forEach { item ->
                    appendLine("- NOTE: ${item.text}")
                }
            }
            if (upcomingEpisodes.isNotEmpty()) {
                if (hasDayContext) appendLine()
                appendLine("EPISODI PROSSIMI ($dayLabel):")
                upcomingEpisodes.forEach { episode ->
                    appendLine("- ${formatEpisodeLine(episode)}")
                }
            }
        }.trim()
    }

    private fun formatEpisodeLine(episode: ActivityLogEntry): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALY)
        val timePrefix = episode.scheduledAtMs?.let { "${timeFormat.format(it)} " }.orEmpty()
        val confidenceLabel = when (episode.confidence) {
            EpisodeConfidence.TENTATIVE -> "tentativo"
            EpisodeConfidence.CONFIRMED -> "confermato"
        }
        val channelSuffix = episode.sourceChannel?.let { ", $it" }.orEmpty()
        val actorPrefix = episode.actor?.let { "$it: " }.orEmpty()
        val snippet = episode.rawPhrase?.let { " — \"$it\"" }.orEmpty()
        val kindHint = when (episode.eventKind) {
            EpisodeKind.SOCIAL_THREAD -> "conversazione aperta"
            EpisodeKind.COMMITMENT -> "impegno"
            EpisodeKind.PLAN -> "piano"
            EpisodeKind.PHYSICAL_NOW -> ""
        }
        val kindSuffix = kindHint.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return "$timePrefix$actorPrefix${episode.label}$snippet$kindSuffix ($confidenceLabel$channelSuffix)"
    }
}
