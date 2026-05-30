package com.example.mydeskrobot.data.scheduled.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mydeskrobot.data.scheduled.ScheduledTaskStatus

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val message: String,
    val triggerAtMillis: Long,
    val status: ScheduledTaskStatus,
    val createdAtMillis: Long,
)
