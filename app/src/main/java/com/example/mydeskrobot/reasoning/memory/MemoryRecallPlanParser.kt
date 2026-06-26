package com.example.mydeskrobot.reasoning.memory

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object MemoryRecallPlanParser {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(MemoryRecallPlanPayload::class.java)

    private val DAY_KEY_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun parse(raw: String): MemoryRecallPlan? {
        val json = stripCodeFence(raw.trim())
        if (json.isBlank()) return null
        val payload = runCatching { adapter.fromJson(json) }.getOrNull() ?: return null
        if (payload.skip_recall == true) {
            return MemoryRecallPlan.skip()
        }
        val scope = parseTemporalScope(payload.temporal_scope) ?: return null
        val focus = RecallFocus.fromJson(payload.recall_focus) ?: return null
        val focusDayKey = payload.focus_day_key?.trim()?.takeIf { it.isNotBlank() }
        if (focusDayKey != null && !DAY_KEY_REGEX.matches(focusDayKey)) return null
        val queries = payload.search_queries
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (queries.isEmpty()) return null
        return MemoryRecallPlan(
            temporalScope = scope,
            focusDayKey = focusDayKey,
            recallFocus = focus,
            searchQueries = queries,
            includeHabitSummary = payload.include_habit_summary ?: false,
            localizeSpatial = payload.localize_spatial ?: false,
        )
    }

    fun stripCodeFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .trim()
            .substringBeforeLast("```")
            .trim()
    }

    private fun parseTemporalScope(raw: String?): TemporalScope? {
        return when (raw?.trim()?.uppercase()) {
            "NONE" -> TemporalScope.NONE
            "SINGLE_DAY" -> TemporalScope.SINGLE_DAY
            "WEEK" -> TemporalScope.WEEK
            "MONTH" -> TemporalScope.MONTH
            else -> null
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class MemoryRecallPlanPayload(
    val skip_recall: Boolean? = null,
    val temporal_scope: String? = null,
    val focus_day_key: String? = null,
    val recall_focus: String? = null,
    val search_queries: List<String>? = null,
    val include_habit_summary: Boolean? = null,
    val localize_spatial: Boolean? = null,
)
