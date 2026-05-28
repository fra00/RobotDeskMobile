package com.example.mydeskrobot.memory.extract

import com.example.mydeskrobot.memory.MemorySettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class MemoryExtractionScheduler(
    private val scope: CoroutineScope,
    private val settingsRepository: MemorySettingsRepository,
    private val extractionService: MemoryExtractionService,
    private val getConversationLog: () -> String,
    private val isStandby: () -> Boolean,
) {
    private var job: Job? = null
    private var lastRunAtMs: Long = 0L

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val settings = settingsRepository.load()
                val waitMs = settings.intervalSeconds.coerceIn(10L, 300L) * 1000L
                delay(waitMs)

                if (!settings.enabled) continue
                if (!isStandby()) continue

                // Basic rate-limit guard.
                val now = System.currentTimeMillis()
                if (now - lastRunAtMs < 30_000L) continue
                lastRunAtMs = now

                val entries = MemoryExtractionService.extractEntriesFromConversationLog(
                    getConversationLog(),
                )
                if (entries.isEmpty()) continue

                val delta = entries.filter { it.id > settings.lastProcessedMessageId }
                if (delta.isEmpty()) continue

                extractionService.processDelta(delta)
                settingsRepository.setLastProcessedMessageId(delta.maxOf { it.id })
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
