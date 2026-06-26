package com.example.mydeskrobot.activity.summary

import android.util.Log
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.activitylog.ActivityLogSettingsRepository
import com.example.mydeskrobot.memory.unified.UnifiedMemoryWriter
import com.example.mydeskrobot.integration.llm.LlmHttpErrors
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityHabitSummarizer(
    private val llmClient: LlmClient,
    private val activityLogRepository: ActivityLogRepository,
    private val settingsRepository: ActivityLogSettingsRepository,
    private val summaryPrompt: String,
    private val memoryWriter: UnifiedMemoryWriter,
) {
    suspend fun currentEventCount(): Int = activityLogRepository.countEventsInRetentionWindow()

    suspend fun refreshSummary(): Boolean {
        if (!llmClient.isConfigured()) return false

        val groups = activityLogRepository.getEventsForSummary()
        if (groups.isEmpty()) {
            Log.d(TAG, "No activity events to summarize")
            return false
        }

        val input = buildSummaryInput(groups)
        val llmResult = llmClient.chat(
            messages = listOf(ConversationMessage.User(input)),
            systemPrompt = summaryPrompt,
        )
        llmResult.exceptionOrNull()?.let { error ->
            Log.w(TAG, "Habit summarizer LLM failed: ${LlmHttpErrors.formatForLog(error)}", error)
            return false
        }
        val summary = llmResult.getOrNull()?.content?.trim().orEmpty()
        if (summary.isBlank()) {
            Log.w(TAG, "Habit summarizer returned empty content")
            return false
        }

        val eventCount = activityLogRepository.countEventsInRetentionWindow()
        activityLogRepository.saveHabitSummary(summary, eventCount)
        memoryWriter.onHabitSummarySaved(
            summaryText = summary,
            sourceEventCount = eventCount,
        )
        settingsRepository.setLastSummaryAt(System.currentTimeMillis())
        settingsRepository.setLastSummaryEventCount(eventCount)
        Log.i(TAG, "Habit summary updated ($eventCount events)")
        return true
    }

    private fun buildSummaryInput(
        groups: List<com.example.mydeskrobot.domain.activitylog.DayActivityGroup>,
    ): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALY)
        return buildString {
            append("Eventi per giorno:\n")
            groups.forEach { group ->
                append("\n${group.dayKey}:\n")
                group.events.forEach { event ->
                    val time = timeFormat.format(event.timestampMs)
                    append("- $time ${event.label}")
                    event.rawPhrase?.let { append(" ($it)") }
                    append('\n')
                }
            }
        }.trim()
    }

    companion object {
        private const val TAG = "ActivityHabitSummary"
    }
}
