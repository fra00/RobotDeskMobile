package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.reasoning.memory.TemporalScope

data class MemoryRecallRequest(
    val query: String,
    val temporalScope: TemporalScope = TemporalScope.NONE,
    val focusDayKey: String? = null,
    val includeVisionCatalog: Boolean = false,
    val localizeQuery: Boolean = false,
    val excludeSpatialLandmarks: Boolean = false,
    val preferEpisodicDetail: Boolean = false,
    val preferUserFacts: Boolean = false,
    val searchQueries: List<String> = emptyList(),
    val includeHabitSummary: Boolean = false,
    val limit: Int = DEFAULT_RECALL_LIMIT,
    val minScore: Float = MemorySearchScorer.DEFAULT_MIN_SCORE,
) {
    companion object {
        const val DEFAULT_RECALL_LIMIT = MemoryRecallBudget.TOTAL
        const val SCOPE_LINKED_SCORE = 0.9f
        const val VISION_CATALOG_SCORE = 0.85f
        const val HABIT_SUMMARY_WIDE_RANGE_SCORE = 0.92f
        const val UNREAD_EPISODE_SCORE = 0.88f
        const val USER_FACT_LINKED_SCORE = 0.91f
    }
}
