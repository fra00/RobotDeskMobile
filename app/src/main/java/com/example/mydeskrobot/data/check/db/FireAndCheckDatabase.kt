package com.example.mydeskrobot.data.check.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.mydeskrobot.data.check.FireAndCheckStatus

@Database(
    entities = [FireAndCheckEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(FireAndCheckConverters::class)
abstract class FireAndCheckDatabase : RoomDatabase() {
    abstract fun fireAndCheckDao(): FireAndCheckDao
}

class FireAndCheckConverters {
    @TypeConverter
    fun toStatus(raw: String): FireAndCheckStatus = FireAndCheckStatus.valueOf(raw)

    @TypeConverter
    fun fromStatus(value: FireAndCheckStatus): String = value.name
}
