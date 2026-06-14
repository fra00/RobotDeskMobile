package com.example.mydeskrobot.data.activitylog.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ActivityLogEventEntity::class,
        ActivityHabitProfileEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ActivityLogConverters::class)
abstract class ActivityLogDatabase : RoomDatabase() {
    abstract fun activityLogDao(): ActivityLogDao
}
