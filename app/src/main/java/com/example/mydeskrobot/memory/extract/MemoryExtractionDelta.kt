package com.example.mydeskrobot.memory.extract

/**
 * Picks unprocessed chat log entries using a stable entry index (not per-parse synthetic ids).
 */
internal object MemoryExtractionDelta {
    fun selectDelta(
        entries: List<ChatLogEntry>,
        processedEntryCount: Long,
    ): List<ChatLogEntry> {
        if (entries.isEmpty()) return emptyList()
        val effectiveProcessed = if (processedEntryCount > entries.size) 0L else processedEntryCount
        return entries.drop(effectiveProcessed.toInt())
    }
}
