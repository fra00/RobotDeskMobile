package com.example.mydeskrobot.data.activitylog.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: ActivityLogEventEntity): Long

    @Query(
        """
        SELECT * FROM activity_log_events
        WHERE timestampMs >= :sinceMs
        ORDER BY timestampMs DESC
        """,
    )
    fun observeSince(sinceMs: Long): Flow<List<ActivityLogEventEntity>>

    @Query(
        """
        SELECT * FROM activity_log_events
        WHERE timestampMs >= :sinceMs
        ORDER BY timestampMs DESC
        """,
    )
    suspend fun getSince(sinceMs: Long): List<ActivityLogEventEntity>

    @Query(
        """
        SELECT * FROM activity_log_events
        WHERE dayKey = :dayKey AND label = :label
        ORDER BY timestampMs DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestByDayAndLabel(dayKey: String, label: String): ActivityLogEventEntity?

    @Query("SELECT COUNT(*) FROM activity_log_events WHERE timestampMs >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Int

    @Query("DELETE FROM activity_log_events WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM activity_log_events")
    suspend fun deleteAllEvents()

    @Query("SELECT * FROM activity_habit_profile WHERE id = :id LIMIT 1")
    suspend fun getProfile(id: Int = ActivityHabitProfileEntity.SINGLETON_ID): ActivityHabitProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: ActivityHabitProfileEntity)

    @Query("DELETE FROM activity_habit_profile")
    suspend fun deleteProfile()
}
