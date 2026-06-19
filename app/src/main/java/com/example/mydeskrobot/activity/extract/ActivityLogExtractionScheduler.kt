package com.example.mydeskrobot.activity.extract

import android.util.Log
import com.example.mydeskrobot.activity.summary.ActivityHabitSummarizer
import com.example.mydeskrobot.data.activitylog.ActivityLogSettingsRepository
import com.example.mydeskrobot.memory.extract.MemoryExtractionDelta
import com.example.mydeskrobot.memory.extract.ConversationLogParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ActivityLogExtractionScheduler(
    private val scope: CoroutineScope,
    private val settingsRepository: ActivityLogSettingsRepository,
    private val extractionService: ActivityExtractionService,
    private val habitSummarizer: ActivityHabitSummarizer,
    private val getConversationLog: () -> String,
    private val isStandby: () -> Boolean,
    private val isLlmConfigured: () -> Boolean,
    private val onExtractingChanged: (Boolean) -> Unit = {},
) {
    private var job: Job? = null
    private var lastRunAtMs: Long = 0L

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                runExtractionCycleIfReady()
                val settings = settingsRepository.load()
                val waitMs = settings.intervalMinutes.coerceIn(5L, 120L) * 60_000L
                delay(waitMs)
            }
        }
    }

    fun requestRunOnce() {
        scope.launch { runExtractionCycleIfReady() }
    }

    fun stop() {
        job?.cancel()
        job = null
        onExtractingChanged(false)
    }

    private suspend fun runExtractionCycleIfReady() {
        val settings = settingsRepository.load()
        if (!settings.enabled) return
        if (!isLlmConfigured()) return
        if (!isStandby()) return

        val now = System.currentTimeMillis()
        if (now - lastRunAtMs < 30_000L) return
        lastRunAtMs = now

        val log = getConversationLog()
        if (log.isBlank()) {
            settingsRepository.setLastProcessedEntryCount(0L)
            maybeRunSummary(settings)
            return
        }

        val entries = MemoryExtractionService.extractEntriesFromConversationLog(log)
        if (entries.isEmpty()) {
            maybeRunSummary(settings)
            return
        }

        var processedCount = settings.lastProcessedEntryCount
        if (processedCount > entries.size) {
            Log.i(TAG, "Log shrank ($processedCount -> ${entries.size}); resetting extraction cursor")
            processedCount = 0L
            settingsRepository.setLastProcessedEntryCount(0L)
        }

        val delta = MemoryExtractionDelta.selectDelta(entries, processedCount)
        if (delta.isEmpty()) {
            maybeRunSummary(settings)
            return
        }

        try {
            onExtractingChanged(true)
            val saved = extractionService.processDelta(delta)
            if (saved > 0) {
                settingsRepository.setLastProcessedEntryCount(entries.size.toLong())
                Log.i(TAG, "Saved $saved episodic event(s) from ${delta.size} log line(s)")
            }
        } finally {
            onExtractingChanged(false)
        }
        maybeRunSummary(settingsRepository.load())
    }

    private suspend fun maybeRunSummary(settings: com.example.mydeskrobot.data.activitylog.ActivityLogSettings) {
        val eventCount = habitSummarizer.currentEventCount()
        val daySinceSummary = System.currentTimeMillis() - settings.lastSummaryAtMs
        val hasNewEvents = eventCount > settings.lastSummaryEventCount
        val dailyDue = settings.lastSummaryAtMs == 0L ||
            daySinceSummary >= TimeUnit.DAYS.toMillis(1)
        if (hasNewEvents && (dailyDue || eventCount - settings.lastSummaryEventCount >= 3)) {
            habitSummarizer.refreshSummary()
        }
    }

    companion object {
        private const val TAG = "ActivityLogExtraction"
    }
}
