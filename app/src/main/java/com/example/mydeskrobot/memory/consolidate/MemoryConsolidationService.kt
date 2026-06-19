package com.example.mydeskrobot.memory.consolidate

import android.util.Log
import com.example.mydeskrobot.integration.llm.LlmHttpErrors
import com.example.mydeskrobot.memory.MemorySettingsRepository
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryConsolidationService(
    private val llmClient: LlmClient,
    private val memoryRepository: UserMemoryRepository,
    private val settingsRepository: MemorySettingsRepository,
    private val systemPrompt: String,
) {
    @Volatile
    private var running = false

    suspend fun consolidateIfNeeded(force: Boolean = false): MemoryConsolidationResult {
        if (!llmClient.isConfigured()) {
            return MemoryConsolidationResult.SkippedNotConfigured
        }
        if (running) {
            return MemoryConsolidationResult.SkippedAlreadyRunning
        }

        val active = memoryRepository.getUserFacingActive()
        if (active.size <= MIN_ROWS_TO_CONSOLIDATE) {
            return MemoryConsolidationResult.SkippedTooFew(active.size)
        }

        val contentHash = memoryRepository.computeUserFacingContentHash(active)
        if (!force) {
            val lastHash = settingsRepository.getLastConsolidatedContentHash()
            if (lastHash != null && lastHash == contentHash) {
                return MemoryConsolidationResult.SkippedUnchanged
            }
        }

        running = true
        try {
            return runConsolidation(active, contentHash)
        } finally {
            running = false
        }
    }

    private suspend fun runConsolidation(
        active: List<MemoryItemEntity>,
        contentHashBefore: String,
    ): MemoryConsolidationResult {
        val userMessage = buildUserMessage(active)
        val llmResult = llmClient.chat(
            messages = listOf(ConversationMessage.User(userMessage)),
            systemPrompt = systemPrompt,
        )
        llmResult.exceptionOrNull()?.let { error ->
            Log.w(TAG, "Consolidation LLM failed: ${LlmHttpErrors.formatForLog(error)}", error)
            return MemoryConsolidationResult.Failed("llm_error")
        }

        val raw = llmResult.getOrNull()?.content?.trim().orEmpty()
        if (raw.isBlank()) {
            return MemoryConsolidationResult.Failed("empty_output")
        }

        val parsed = MemoryConsolidationParser.parseLines(raw)
        if (parsed.isEmpty()) {
            Log.w(TAG, "Consolidation parse produced no lines: ${raw.take(120)}")
            return MemoryConsolidationResult.Failed("parse_empty")
        }

        if (!isOutputRatioAcceptable(before = active.size, after = parsed.size)) {
            Log.w(
                TAG,
                "Consolidation output too aggressive: before=${active.size} after=${parsed.size}",
            )
            return MemoryConsolidationResult.Failed("output_too_aggressive")
        }

        settingsRepository.saveConsolidationBackup(active)
        val replaced = memoryRepository.replaceUserFacingWithConsolidated(parsed)
        if (replaced <= 0) {
            return MemoryConsolidationResult.Failed("apply_failed")
        }

        val hashAfter = memoryRepository.computeUserFacingContentHash(
            memoryRepository.getUserFacingActive(),
        )
        settingsRepository.setLastConsolidatedContentHash(hashAfter.ifBlank { contentHashBefore })
        Log.i(
            TAG,
            "Consolidation applied: ${active.size} -> $replaced rows (parsed=${parsed.size})",
        )
        return MemoryConsolidationResult.Success(
            before = active.size,
            after = replaced,
        )
    }

    private fun buildUserMessage(memories: List<MemoryItemEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ITALIAN)
        val lines = memories
            .sortedByDescending { it.updatedAt }
            .joinToString("\n") { memory ->
                val date = dateFormat.format(Date(memory.updatedAt))
                "[$date] (${memory.category.name}) ${memory.value}"
            }
        return buildString {
            appendLine("MEMORIES TO CONSOLIDATE:")
            append(lines)
        }.trim()
    }

    private fun isOutputRatioAcceptable(before: Int, after: Int): Boolean {
        if (after == 0) return false
        if (before <= 5) return after >= 1
        return after >= (before * MIN_OUTPUT_RATIO).toInt().coerceAtLeast(1)
    }

    companion object {
        private const val TAG = "MemoryConsolidation"
        const val MIN_ROWS_TO_CONSOLIDATE = 3
        private const val MIN_OUTPUT_RATIO = 0.15f
    }
}

sealed class MemoryConsolidationResult {
    data object SkippedNotConfigured : MemoryConsolidationResult()
    data object SkippedAlreadyRunning : MemoryConsolidationResult()
    data object SkippedUnchanged : MemoryConsolidationResult()
    data class SkippedTooFew(val count: Int) : MemoryConsolidationResult()
    data class Success(val before: Int, val after: Int) : MemoryConsolidationResult()
    data class Failed(val reason: String) : MemoryConsolidationResult()
}
