package com.example.mydeskrobot.memory.consolidate

import com.example.mydeskrobot.memory.MemoryDuplicateDetector
import com.example.mydeskrobot.memory.MemoryTopicMatcher
import com.example.mydeskrobot.memory.db.MemoryCategory

/**
 * Plans in-place consolidation: untouched rows stay identical; only merged duplicates are removed.
 */
object MemoryConsolidationApplicator {

    private const val MIN_MATCH_SCORE = 0.34f

    private val GENERIC_MATCH_TOKENS = setOf(
        "chiama", "utente", "lavora", "ogni", "dell", "della", "sono", "nome",
    )

    data class MemoryRow(
        val id: Long,
        val category: MemoryCategory,
        val value: String,
        val useCount: Int = 0,
        val lastUsedAt: Long = 0L,
        val updatedAt: Long = 0L,
        val createdAt: Long = 0L,
    )

    data class RowUpdate(
        val category: MemoryCategory,
        val value: String,
        val useCount: Int,
        val lastUsedAt: Long,
    )

    data class ApplyPlan(
        val unchangedIds: Set<Long> = emptySet(),
        val updates: Map<Long, RowUpdate> = emptyMap(),
        val deactivateIds: Set<Long> = emptySet(),
        val inserts: List<ConsolidatedMemoryLine> = emptyList(),
    )

    fun plan(active: List<MemoryRow>, consolidated: List<ConsolidatedMemoryLine>): ApplyPlan {
        if (consolidated.isEmpty()) return ApplyPlan()
        val claimed = mutableSetOf<Long>()
        val unchanged = mutableSetOf<Long>()
        val updates = mutableMapOf<Long, RowUpdate>()
        val deactivate = mutableSetOf<Long>()
        val inserts = mutableListOf<ConsolidatedMemoryLine>()

        for (line in consolidated) {
            val cluster = active.filter { row ->
                row.id !in claimed &&
                    row.id !in deactivate &&
                    matches(row, line)
            }
            if (cluster.isEmpty()) {
                inserts += line
                continue
            }
            val keeper = cluster.maxWithOrNull(
                compareBy<MemoryRow> { it.useCount }
                    .thenBy { it.lastUsedAt }
                    .thenBy { it.updatedAt },
            ) ?: continue
            claimed += keeper.id
            cluster.filter { it.id != keeper.id }.forEach { duplicate ->
                claimed += duplicate.id
                deactivate += duplicate.id
            }
            val mergedUseCount = cluster.sumOf { it.useCount }
            val mergedLastUsed = cluster.maxOf { it.lastUsedAt }
            if (isSameContent(keeper, line) && keeper.category == line.category) {
                if (mergedUseCount != keeper.useCount || mergedLastUsed != keeper.lastUsedAt) {
                    updates[keeper.id] = RowUpdate(
                        category = keeper.category,
                        value = keeper.value,
                        useCount = mergedUseCount,
                        lastUsedAt = mergedLastUsed,
                    )
                } else {
                    unchanged += keeper.id
                }
            } else {
                updates[keeper.id] = RowUpdate(
                    category = line.category,
                    value = line.value.trim(),
                    useCount = mergedUseCount,
                    lastUsedAt = mergedLastUsed,
                )
            }
        }

        return ApplyPlan(
            unchangedIds = unchanged,
            updates = updates,
            deactivateIds = deactivate,
            inserts = inserts,
        )
    }

    private fun matches(row: MemoryRow, line: ConsolidatedMemoryLine): Boolean {
        if (MemoryDuplicateDetector.areDuplicates(row.value, line.value, line.category)) return true
        if (MemoryDuplicateDetector.areDuplicates(row.value, line.value, row.category)) return true
        return topicMatchSameCategory(row, line) || isAbsorbedByConsolidatedLine(row, line)
    }

    private fun topicMatchSameCategory(row: MemoryRow, line: ConsolidatedMemoryLine): Boolean {
        if (row.category != line.category) return false
        val score = MemoryTopicMatcher.score(row.value, line.value)
        if (score < MIN_MATCH_SCORE) return false
        val distinctiveTokens = MemoryTopicMatcher.tokenize(row.value)
            .filter { it.length >= 4 }
            .filter { it !in GENERIC_MATCH_TOKENS }
        if (distinctiveTokens.isEmpty()) return score >= 0.5f
        val output = line.value.lowercase()
        return distinctiveTokens.any { output.contains(it) }
    }

    private fun isAbsorbedByConsolidatedLine(row: MemoryRow, line: ConsolidatedMemoryLine): Boolean {
        if (row.category != line.category) return false
        val inputTokens = MemoryTopicMatcher.tokenize(row.value).filter { it.length >= 3 }
        if (inputTokens.isEmpty()) return false
        val output = line.value.lowercase()
        val hit = inputTokens.count { output.contains(it) }
        return hit.toFloat() / inputTokens.size >= 0.5f
    }

    private fun isSameContent(row: MemoryRow, line: ConsolidatedMemoryLine): Boolean =
        normalize(row.value) == normalize(line.value)

    private fun normalize(value: String): String =
        value.trim().trimEnd('.', ';', ',').lowercase()
}
