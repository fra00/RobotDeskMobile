package com.example.mydeskrobot.data.check.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mydeskrobot.data.check.FireAndCheckStatus
import com.example.mydeskrobot.domain.check.FireAndCheckKind

@Database(
    entities = [FireAndCheckEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(FireAndCheckConverters::class)
abstract class FireAndCheckDatabase : RoomDatabase() {
    abstract fun fireAndCheckDao(): FireAndCheckDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE fire_and_check ADD COLUMN kind TEXT NOT NULL DEFAULT 'PHYSICAL'",
                )
                db.execSQL(
                    "ALTER TABLE fire_and_check ADD COLUMN reRemindCount INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}

class FireAndCheckConverters {
    @TypeConverter
    fun toStatus(raw: String): FireAndCheckStatus = FireAndCheckStatus.valueOf(raw)

    @TypeConverter
    fun fromStatus(value: FireAndCheckStatus): String = value.name

    @TypeConverter
    fun toKind(raw: String): FireAndCheckKind =
        runCatching { FireAndCheckKind.valueOf(raw) }.getOrDefault(FireAndCheckKind.PHYSICAL)

    @TypeConverter
    fun fromKind(value: FireAndCheckKind): String = value.name
}
