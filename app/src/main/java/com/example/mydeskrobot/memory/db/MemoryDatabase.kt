package com.example.mydeskrobot.memory.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MemoryItemEntity::class],
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE memory_items ADD COLUMN useCount INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memory_items_useCount ON memory_items(useCount)",
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
