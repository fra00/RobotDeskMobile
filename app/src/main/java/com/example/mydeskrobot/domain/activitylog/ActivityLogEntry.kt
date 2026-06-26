package com.example.mydeskrobot.domain.activitylog

data class ActivityLogEntry(
    val id: Long,
    val dayKey: String,
    val timestampMs: Long,
    val label: String,
    val rawPhrase: String?,
    val source: ActivitySource,
    val eventKind: EpisodeKind = EpisodeKind.PHYSICAL_NOW,
    val confidence: EpisodeConfidence = EpisodeConfidence.CONFIRMED,
    val scheduledAtMs: Long? = null,
    val scheduledDayKey: String? = null,
    val actor: String? = null,
    val sourceChannel: String? = null,
    val isUnread: Boolean = false,
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
