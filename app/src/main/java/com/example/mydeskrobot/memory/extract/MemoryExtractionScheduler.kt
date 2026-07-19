package com.example.mydeskrobot.memory.extract

import android.util.Log
import com.example.mydeskrobot.memory.MemorySettingsRepository
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class MemoryExtractionScheduler(
    private val scope: CoroutineScope,
    private val settingsRepository: MemorySettingsRepository,
    private val extractionService: MemoryExtractionService,
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
    private val getConversationLog: () -> String,
    private val isStandby: () -> Boolean,
    private val onExtractingChanged: (Boolean) -> Unit = {},
    private val onAfterCycle: (suspend () -> Unit)? = null,
) {
    private var job: Job? = null
    private var lastRunAtMs: Long = 0L

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                if (isStandby()) {
                    runExtractionCycleIfReady()
                    onAfterCycle?.invoke()
                }
                val settings = settingsRepository.load()
                val waitMs = settings.intervalSeconds.coerceIn(10L, 300L) * 1000L
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
        if (!isStandby()) return

        val now = System.currentTimeMillis()
        if (now - lastRunAtMs < 30_000L) return
        lastRunAtMs = now

        val log = getConversationLog()
        if (log.isBlank()) {
            settingsRepository.setLastProcessedEntryCount(0L)
            runPruneIfNeeded()
            return
        }

        val entries = MemoryExtractionService.extractEntriesFromConversationLog(log)
        if (entries.isEmpty()) {
            runPruneIfNeeded()
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
            runPruneIfNeeded()
            return
        }

        try {
            onExtractingChanged(true)
            val saved = extractionService.processDelta(delta)
            if (saved > 0) {
                settingsRepository.setLastProcessedEntryCount(entries.size.toLong())
                Log.i(TAG, "Saved $saved memory fact(s) from ${delta.size} log line(s)")
            } else {
                Log.w(TAG, "Extraction produced no facts for ${delta.size} log line(s)")
            }
        } finally {
            onExtractingChanged(false)
        }
        runPruneIfNeeded()
    }

    private suspend fun runPruneIfNeeded() {
        val pruned = unifiedMemoryRepository.pruneIfNeeded(
            UnifiedMemoryRepository.USER_FACING_MAX_ITEMS,
        )
        if (pruned > 0) {
            Log.i(TAG, "Pruned $pruned low-use memory row(s) over cap")
        }
    }

    companion object {
        private const val TAG = "MemoryExtraction"
    }
}
