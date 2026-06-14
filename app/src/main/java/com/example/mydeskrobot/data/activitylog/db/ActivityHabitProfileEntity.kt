package com.example.mydeskrobot.data.activitylog.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_habit_profile")
data class ActivityHabitProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val summaryText: String,
    val updatedAtMs: Long,
    val sourceEventCount: Int,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
