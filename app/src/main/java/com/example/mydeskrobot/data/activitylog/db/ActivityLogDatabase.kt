package com.example.mydeskrobot.data.activitylog.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ActivityLogEventEntity::class,
        ActivityHabitProfileEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(ActivityLogConverters::class)
abstract class ActivityLogDatabase : RoomDatabase() {
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE activity_log_events ADD COLUMN eventKind TEXT NOT NULL DEFAULT 'PHYSICAL_NOW'",
                )
                db.execSQL(
                    "ALTER TABLE activity_log_events ADD COLUMN confidence TEXT NOT NULL DEFAULT 'CONFIRMED'",
                )
                db.execSQL(
                    "ALTER TABLE activity_log_events ADD COLUMN scheduledAtMs INTEGER DEFAULT NULL",
                )
                db.execSQL(
                    "ALTER TABLE activity_log_events ADD COLUMN scheduledDayKey TEXT DEFAULT NULL",
                )
                db.execSQL(
                    "ALTER TABLE activity_log_events ADD COLUMN actor TEXT DEFAULT NULL",
                )
                db.execSQL(
                    "ALTER TABLE activity_log_events ADD COLUMN sourceChannel TEXT DEFAULT NULL",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_activity_log_events_scheduledDayKey " +
                        "ON activity_log_events(scheduledDayKey)",
                )
            }
        }
    }
}
