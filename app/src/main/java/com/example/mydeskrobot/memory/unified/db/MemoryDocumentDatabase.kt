package com.example.mydeskrobot.memory.unified.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MemoryDocumentEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MemoryDocumentDatabase : RoomDatabase() {
    abstract fun memoryDocumentDao(): MemoryDocumentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE memory_documents ADD COLUMN isUnread INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE memory_documents ADD COLUMN linkedActivityLogId INTEGER DEFAULT NULL",
                )
            }
        }
    }
}
