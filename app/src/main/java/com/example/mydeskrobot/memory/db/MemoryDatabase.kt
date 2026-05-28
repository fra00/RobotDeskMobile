package com.example.mydeskrobot.memory.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [MemoryItemEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(MemoryConverters::class)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}

class MemoryConverters {
    @TypeConverter
    fun toCategory(raw: String): MemoryCategory = MemoryCategory.valueOf(raw)

    @TypeConverter
    fun fromCategory(value: MemoryCategory): String = value.name
}
