package com.example.mydeskrobot.data.activitylog.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mydeskrobot.domain.activitylog.ActivitySource

@Entity(
    tableName = "activity_log_events",
    indices = [
        Index(value = ["dayKey"]),
        Index(value = ["timestampMs"]),
    ],
)
data class ActivityLogEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val timestampMs: Long,
    val label: String,
    val rawPhrase: String?,
    val source: ActivitySource,
)
