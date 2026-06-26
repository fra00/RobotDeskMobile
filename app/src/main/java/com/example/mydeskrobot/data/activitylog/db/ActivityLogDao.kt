package com.example.mydeskrobot.data.activitylog.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: ActivityLogEventEntity): Long

    @Update
    suspend fun update(event: ActivityLogEventEntity)

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

    @Query(
        """
        SELECT * FROM activity_log_events
        WHERE scheduledDayKey = :scheduledDayKey
          AND eventKind = :eventKind
          AND label = :label
          AND (
            (:actor IS NULL AND actor IS NULL)
            OR (actor IS NOT NULL AND :actor IS NOT NULL AND actor = :actor)
          )
        ORDER BY timestampMs DESC
        LIMIT 1
        """,
    )
    suspend fun findEpisodicForMerge(
        scheduledDayKey: String,
        eventKind: EpisodeKind,
        label: String,
        actor: String?,
    ): ActivityLogEventEntity?

    @Query(
        """
        SELECT * FROM activity_log_events
        WHERE scheduledDayKey = :targetDayKey
          AND eventKind IN ('PLAN', 'SOCIAL_THREAD', 'COMMITMENT')
        ORDER BY
          CASE WHEN scheduledAtMs IS NULL THEN 1 ELSE 0 END,
          scheduledAtMs ASC,
          timestampMs DESC
        LIMIT :limit
        """,
    )
    suspend fun getUpcomingForDay(targetDayKey: String, limit: Int): List<ActivityLogEventEntity>

    @Query(
        """
        SELECT * FROM activity_log_events
        WHERE eventKind = 'SOCIAL_THREAD'
          AND timestampMs >= :sinceMs
        ORDER BY timestampMs DESC
        LIMIT :limit
        """,
    )
    suspend fun getOpenSocialThreads(sinceMs: Long, limit: Int): List<ActivityLogEventEntity>

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

    @Query("SELECT * FROM activity_log_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ActivityLogEventEntity?
}
