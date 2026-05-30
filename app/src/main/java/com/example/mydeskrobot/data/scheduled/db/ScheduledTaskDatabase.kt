package com.example.mydeskrobot.data.scheduled.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.mydeskrobot.data.scheduled.ScheduledTaskStatus

@Database(
    entities = [ScheduledTaskEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ScheduledTaskConverters::class)
abstract class ScheduledTaskDatabase : RoomDatabase() {
    abstract fun scheduledTaskDao(): ScheduledTaskDao
}

class ScheduledTaskConverters {
    @TypeConverter
    fun toStatus(raw: String): ScheduledTaskStatus = ScheduledTaskStatus.valueOf(raw)

    @TypeConverter
    fun fromStatus(value: ScheduledTaskStatus): String = value.name
}
