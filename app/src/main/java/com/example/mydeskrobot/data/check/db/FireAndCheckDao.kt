package com.example.mydeskrobot.data.check.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mydeskrobot.data.check.FireAndCheckStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FireAndCheckDao {

    @Insert
    suspend fun insert(entity: FireAndCheckEntity): Long

    @Update
    suspend fun update(entity: FireAndCheckEntity)

    @Query(
        """
        SELECT * FROM fire_and_check
        WHERE status = :status
        ORDER BY createdAtMillis ASC
        """,
    )
    fun observeByStatus(status: FireAndCheckStatus): Flow<List<FireAndCheckEntity>>

    @Query(
        """
        SELECT * FROM fire_and_check
        WHERE status = :status
        ORDER BY createdAtMillis ASC
        """,
    )
    suspend fun listByStatus(status: FireAndCheckStatus): List<FireAndCheckEntity>

    @Query("SELECT * FROM fire_and_check WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FireAndCheckEntity?

    @Query(
        """
        SELECT * FROM fire_and_check
        WHERE status = :status
        AND primaryReminderId IS NOT NULL
        AND verificationReminderId IS NULL
        ORDER BY createdAtMillis DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestAwaitingVerificationLink(
        status: FireAndCheckStatus = FireAndCheckStatus.ACTIVE,
    ): FireAndCheckEntity?

    @Query("SELECT * FROM fire_and_check WHERE primaryReminderId = :reminderId LIMIT 1")
    suspend fun findByPrimaryReminderId(reminderId: Long): FireAndCheckEntity?

    @Query("SELECT * FROM fire_and_check WHERE verificationReminderId = :reminderId LIMIT 1")
    suspend fun findByVerificationReminderId(reminderId: Long): FireAndCheckEntity?

    @Query("UPDATE fire_and_check SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: FireAndCheckStatus)
}
