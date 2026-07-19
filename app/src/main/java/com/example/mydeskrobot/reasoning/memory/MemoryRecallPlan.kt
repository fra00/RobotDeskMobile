package com.example.mydeskrobot.reasoning.memory

import com.example.mydeskrobot.memory.unified.MemoryRecallRequest
import com.example.mydeskrobot.reasoning.MemoryContextOptions

data class MemoryRecallPlan(
    val temporalScope: TemporalScope = TemporalScope.NONE,
    val focusDayKey: String? = null,
    val recallFocus: RecallFocus = RecallFocus.GENERAL,
    val searchQueries: List<String> = emptyList(),
    val includeHabitSummary: Boolean = false,
    val localizeSpatial: Boolean = false,
    val includeVisionCatalog: Boolean = false,
    val skipRecall: Boolean = false,
) {
    fun toRequest(
        userText: String,
        options: MemoryContextOptions = MemoryContextOptions(),
    ): MemoryRecallRequest {
        require(!skipRecall) { "skipRecall plan must not be converted to MemoryRecallRequest" }
        var scope = temporalScope
        if (recallFocus == RecallFocus.MESSAGES && scope == TemporalScope.NONE) {
            scope = TemporalScope.WEEK
        }
        if (recallFocus == RecallFocus.GENERAL && scope == TemporalScope.NONE) {
            scope = TemporalScope.WEEK
        }
        val habitSummary = includeHabitSummary ||
            (recallFocus == RecallFocus.GENERAL && scope == TemporalScope.WEEK)
        val queries = searchQueries
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                userText.trim().takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
            }
        return MemoryRecallRequest(
            query = userText,
            temporalScope = scope,
            focusDayKey = focusDayKey,
            includeVisionCatalog = includeVisionCatalog,
            localizeQuery = localizeSpatial,
            excludeSpatialLandmarks = options.freshVisionVerify,
            preferEpisodicDetail = recallFocus == RecallFocus.EPISODIC,
            preferUserFacts = recallFocus == RecallFocus.USER_FACTS,
            searchQueries = queries,
            includeHabitSummary = habitSummary,
        )
    }

    companion object {
        fun visionCatalog(): MemoryRecallPlan = MemoryRecallPlan(
            recallFocus = RecallFocus.GENERAL,
            searchQueries = listOf("persone animali oggetti stanza laboratorio"),
            includeVisionCatalog = true,
        )

        fun skip(): MemoryRecallPlan = MemoryRecallPlan(skipRecall = true)
    }
}
