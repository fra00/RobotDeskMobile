package com.example.mydeskrobot.memory.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MemoryItemEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(MemoryConverters::class)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE memory_items ADD COLUMN expiresAt INTEGER DEFAULT NULL",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memory_items_expiresAt ON memory_items(expiresAt)",
                )
            }
        }
    }
}

class MemoryConverters {
    @TypeConverter
    fun toCategory(raw: String): MemoryCategory = MemoryCategory.valueOf(raw)

    @TypeConverter
    fun fromCategory(value: MemoryCategory): String = value.name
}
