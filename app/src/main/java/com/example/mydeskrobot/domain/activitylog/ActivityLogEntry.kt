package com.example.mydeskrobot.domain.activitylog

data class ActivityLogEntry(
    val id: Long,
    val dayKey: String,
    val timestampMs: Long,
    val label: String,
    val rawPhrase: String?,
    val source: ActivitySource,
)

data class DayActivityGroup(
    val dayKey: String,
    val events: List<ActivityLogEntry>,
)

data class ActivityHabitProfile(
    val summaryText: String,
    val updatedAtMs: Long,
    val sourceEventCount: Int,
)
