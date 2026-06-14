package com.example.mydeskrobot.data.scheduled.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mydeskrobot.data.scheduled.ScheduledTaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {

    @Insert
    suspend fun insert(entity: ScheduledTaskEntity): Long

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduledTaskEntity?

    @Query(
        """
        SELECT * FROM scheduled_tasks
        WHERE status = :status
        ORDER BY triggerAtMillis ASC
        """,
    )
    suspend fun getByStatus(status: ScheduledTaskStatus): List<ScheduledTaskEntity>

    @Query(
        """
        SELECT * FROM scheduled_tasks
        WHERE status = :status
        ORDER BY triggerAtMillis ASC
        """,
    )
    fun observeByStatus(status: ScheduledTaskStatus): Flow<List<ScheduledTaskEntity>>

    @Query(
        """
        SELECT * FROM scheduled_tasks
        WHERE status = :status AND triggerAtMillis > :nowMillis
        ORDER BY triggerAtMillis ASC
        """,
    )
    suspend fun getPendingFuture(
        status: ScheduledTaskStatus,
        nowMillis: Long,
    ): List<ScheduledTaskEntity>

    @Query(
        """
        SELECT * FROM scheduled_tasks
        WHERE status = :status
        AND triggerAtMillis >= :startOfDayMillis
        AND triggerAtMillis < :endOfDayMillis
        ORDER BY triggerAtMillis ASC
        """,
    )
    suspend fun getPendingBetween(
        status: ScheduledTaskStatus,
        startOfDayMillis: Long,
        endOfDayMillis: Long,
    ): List<ScheduledTaskEntity>

    @Update
    suspend fun update(entity: ScheduledTaskEntity)

    @Query("UPDATE scheduled_tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ScheduledTaskStatus)
}
