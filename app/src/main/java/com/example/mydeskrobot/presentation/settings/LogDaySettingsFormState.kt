package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.data.activitylog.ActivityLogSettings

data class LogDaySettingsFormState(
    val enabled: Boolean = true,
    val intervalMinutes: Long = 15L,
    val habitSummary: String? = null,
    val habitSummaryUpdatedAt: Long? = null,
    val isRefreshingSummary: Boolean = false,
    val isLoading: Boolean = false,
)

fun ActivityLogSettings.toLogDayFormState(
    habitSummary: String? = null,
    habitSummaryUpdatedAt: Long? = null,
): LogDaySettingsFormState = LogDaySettingsFormState(
    enabled = enabled,
    intervalMinutes = intervalMinutes,
    habitSummary = habitSummary,
    habitSummaryUpdatedAt = habitSummaryUpdatedAt,
)

data class ActivityLogItemUi(
    val id: Long,
    val timeLabel: String,
    val label: String,
    val sourceLabel: String,
    val rawPhrase: String? = null,
    val episodeKindLabel: String? = null,
    val confidenceLabel: String? = null,
    val scheduledLabel: String? = null,
)

data class DayActivityGroupUi(
    val dayLabel: String,
    val events: List<ActivityLogItemUi>,
)
