package com.example.mydeskrobot.data.activitylog.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind

@Entity(
    tableName = "activity_log_events",
    indices = [
        Index(value = ["dayKey"]),
        Index(value = ["timestampMs"]),
        Index(value = ["scheduledDayKey"]),
    ],
)
data class ActivityLogEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
