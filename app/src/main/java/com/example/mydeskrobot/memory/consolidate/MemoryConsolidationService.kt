package com.example.mydeskrobot.memory.consolidate

import android.util.Log
import com.example.mydeskrobot.integration.llm.LlmHttpErrors
import com.example.mydeskrobot.memory.MemoryConsolidationSettingsStore
import com.example.mydeskrobot.memory.MemoryReorganizePolicy
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MemoryConsolidationService(
    private val llmClient: LlmClient,
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
    private val settingsRepository: MemoryConsolidationSettingsStore,
    private val systemPrompt: String,
) {
    private val consolidationMutex = Mutex()

    suspend fun consolidateIfNeeded(
        force: Boolean = false,
        minRowsToConsolidate: Int = DEFAULT_MIN_ROWS_TO_CONSOLIDATE,
    ): MemoryConsolidationResult {
        if (!llmClient.isConfigured()) {
            return MemoryConsolidationResult.SkippedNotConfigured
        }

        return consolidationMutex.withLock {
            unifiedMemoryRepository.ensureMigrated()
            val active = unifiedMemoryRepository.getUserFacingActiveDocuments()
            val consolidatable = active.filterNot { it.isPinned }
            if (consolidatable.size < minRowsToConsolidate) {
                return@withLock MemoryConsolidationResult.SkippedTooFew(consolidatable.size)
            }

            val contentHash = unifiedMemoryRepository.computeUserFacingContentHash(active)
            if (!force) {
                val lastHash = settingsRepository.getLastConsolidatedContentHash()
                if (lastHash != null && lastHash == contentHash) {
                    return@withLock MemoryConsolidationResult.SkippedUnchanged
                }
            }

            runConsolidation(active, consolidatable, contentHash)
        }
    }

    private suspend fun runConsolidation(
        active: List<MemoryDocumentEntity>,
        consolidatable: List<MemoryDocumentEntity>,
        contentHashBefore: String,
    ): MemoryConsolidationResult {
        val userMessage = buildUserMessageFromUnified(consolidatable)
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

        val inputLines = consolidatable.mapNotNull { doc ->
            val categoryName = doc.category?.trim().orEmpty()
            if (categoryName.isBlank()) return@mapNotNull null
            val category = runCatching { MemoryCategory.valueOf(categoryName) }.getOrNull()
                ?: return@mapNotNull null
            MemoryConsolidationCoverage.InputLine(category = category, value = doc.value)
        }
        val safeParsed = MemoryConsolidationCoverage.appendUncoveredInputLines(
            input = inputLines,
            consolidated = parsed,
        )
        val survivors = safeParsed.size - parsed.size
        if (survivors > 0) {
            Log.w(TAG, "Consolidation coverage guard re-appended $survivors input line(s)")
        }

        val beforeCount = consolidatable.size
        if (!isOutputRatioAcceptable(before = beforeCount, after = safeParsed.size)) {
            Log.w(
                TAG,
                "Consolidation output too aggressive: before=$beforeCount after=${safeParsed.size}",
            )
            return MemoryConsolidationResult.Failed("output_too_aggressive")
        }

        settingsRepository.saveConsolidationBackup(backupEntitiesFromUnified(active))
        val afterCount = unifiedMemoryRepository.replaceUserFacingWithConsolidated(safeParsed)
        if (afterCount <= 0) {
            return MemoryConsolidationResult.Failed("unified_apply_failed")
        }

        val hashAfter = unifiedMemoryRepository.computeUserFacingContentHash()
        settingsRepository.setLastConsolidatedContentHash(hashAfter.ifBlank { contentHashBefore })
        Log.i(
            TAG,
            "Consolidation applied: $beforeCount -> $afterCount rows (parsed=${safeParsed.size}, llm=${parsed.size})",
        )
        return MemoryConsolidationResult.Success(
            before = beforeCount,
            after = afterCount,
        )
    }

    private fun backupEntitiesFromUnified(docs: List<MemoryDocumentEntity>): List<MemoryItemEntity> {
        val now = System.currentTimeMillis()
        return docs.mapNotNull { doc ->
            val categoryName = doc.category?.trim().orEmpty()
            if (categoryName.isBlank()) return@mapNotNull null
            val category = runCatching { MemoryCategory.valueOf(categoryName) }.getOrNull()
                ?: return@mapNotNull null
            MemoryItemEntity(
                category = category,
                value = doc.value,
                confidence = doc.confidence,
                createdAt = doc.createdAt,
                updatedAt = doc.updatedAt,
                useCount = doc.useCount,
                lastUsedAt = doc.lastUsedAt,
                sourceMessageId = 0L,
            )
        }.ifEmpty {
            listOf(
                MemoryItemEntity(
                    category = MemoryCategory.FACT,
                    value = "backup-empty",
                    confidence = 1f,
                    createdAt = now,
                    updatedAt = now,
                    sourceMessageId = 0L,
                ),
            )
        }
    }

    private fun buildUserMessageFromUnified(memories: List<MemoryDocumentEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ITALIAN)
        val lines = memories
            .sortedByDescending { it.updatedAt }
            .joinToString("\n") { memory ->
                val date = dateFormat.format(Date(memory.updatedAt))
                val category = memory.category ?: MemoryCategory.FACT.name
                "[$date] ($category) ${memory.value}"
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
        const val DEFAULT_MIN_ROWS_TO_CONSOLIDATE = MemoryReorganizePolicy.DEFAULT_MIN_USER_FACING_ROWS
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
