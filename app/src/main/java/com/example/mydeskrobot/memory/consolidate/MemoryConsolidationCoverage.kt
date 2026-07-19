package com.example.mydeskrobot.memory.consolidate

import com.example.mydeskrobot.memory.MemoryDuplicateDetector
import com.example.mydeskrobot.memory.MemoryTopicMatcher
import com.example.mydeskrobot.memory.RoutineWeekdayScope
import com.example.mydeskrobot.memory.db.MemoryCategory

/**
 * Ensures LLM consolidation output does not drop input facts the model failed to merge or repeat.
 */
object MemoryConsolidationCoverage {

    private const val MIN_COVERAGE_SCORE = 0.34f

    data class InputLine(
        val category: MemoryCategory,
        val value: String,
    )

    fun isInputCoveredByLine(
        category: MemoryCategory,
        value: String,
        line: ConsolidatedMemoryLine,
    ): Boolean = isCoveredByConsolidated(category, value, listOf(line))

    fun appendUncoveredInputLines(
        input: List<InputLine>,
        consolidated: List<ConsolidatedMemoryLine>,
    ): List<ConsolidatedMemoryLine> {
        if (input.isEmpty()) return consolidated
        val merged = consolidated.toMutableList()
        for (line in input) {
            val value = line.value.trim()
            if (value.isBlank()) continue
            if (isCoveredByConsolidated(line.category, value, merged)) continue
            merged += ConsolidatedMemoryLine(category = line.category, value = value)
        }
        return dedupeMerged(merged)
    }

    private fun isCoveredByConsolidated(
        category: MemoryCategory,
        value: String,
        consolidated: List<ConsolidatedMemoryLine>,
    ): Boolean = consolidated.any { output ->
        if (category == MemoryCategory.ROUTINE &&
            RoutineWeekdayScope.hasDistinctWeekdayScope(value, output.value)
        ) {
            return@any false
        }
        MemoryDuplicateDetector.areDuplicates(value, output.value, category) ||
            normalizeForCoverage(value) == normalizeForCoverage(output.value) ||
            MemoryTopicMatcher.score(value, output.value) >= MIN_COVERAGE_SCORE
    }

    private fun normalizeForCoverage(value: String): String =
        value.trim()
            .trimEnd('.', ';', ',')
            .lowercase()

    private fun dedupeMerged(lines: List<ConsolidatedMemoryLine>): List<ConsolidatedMemoryLine> {
        val seen = linkedSetOf<String>()
        val result = mutableListOf<ConsolidatedMemoryLine>()
        for (line in lines) {
            val key = "${line.category.name}|${line.value.trim().lowercase()}"
            if (seen.add(key)) result += line
        }
        return result
    }
}
