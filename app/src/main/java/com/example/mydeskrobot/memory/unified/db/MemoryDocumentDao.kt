package com.example.mydeskrobot.memory.unified.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MemoryDocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryDocumentEntity): Long

    @Update
    suspend fun update(entity: MemoryDocumentEntity)

    @Query("SELECT * FROM memory_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MemoryDocumentEntity?

    @Query(
        """
        SELECT * FROM memory_documents
        WHERE externalRef = :externalRef
        LIMIT 1
        """,
    )
    suspend fun getByExternalRef(externalRef: String): MemoryDocumentEntity?

    @Query(
        """
        SELECT * FROM memory_documents
        WHERE isActive = 1
        AND (expiresAt IS NULL OR expiresAt > :now)
        """,
    )
    suspend fun getAllActive(now: Long = System.currentTimeMillis()): List<MemoryDocumentEntity>

    @Query("SELECT COUNT(*) FROM memory_documents WHERE isActive = 1")
    suspend fun countActive(): Int

    @Query(
        """
        UPDATE memory_documents
        SET isActive = 0, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun deactivateById(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE memory_documents
        SET isActive = 0, updatedAt = :now
        WHERE externalRef = :externalRef AND isActive = 1
        """,
    )
    suspend fun deactivateByExternalRef(externalRef: String, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE memory_documents
        SET useCount = useCount + 1, lastUsedAt = :now
        WHERE id IN (:ids)
        """,
    )
    suspend fun markUsed(ids: List<Long>, now: Long)

    @Query(
        """
        SELECT * FROM memory_documents
        WHERE kind = :kind
        AND isActive = 1
        AND (expiresAt IS NULL OR expiresAt > :now)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getActiveByKind(
        kind: String,
        limit: Int,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryDocumentEntity>

    @Query(
        """
        SELECT * FROM memory_documents
        WHERE kind = :kind
        AND scheduledDayKey = :scheduledDayKey
        AND isActive = 1
        AND (expiresAt IS NULL OR expiresAt > :now)
        ORDER BY scheduledAtMs ASC, updatedAt DESC
        """,
    )
    suspend fun getActiveByKindAndScheduledDay(
        kind: String,
        scheduledDayKey: String,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryDocumentEntity>

    @Query(
        """
        SELECT * FROM memory_documents
        WHERE kind = :kind
        AND isUnread = 1
        AND isActive = 1
        AND (expiresAt IS NULL OR expiresAt > :now)
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getUnreadByKind(
        kind: String,
        limit: Int,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryDocumentEntity>

    @Query(
        """
        UPDATE memory_documents
        SET isUnread = 0, updatedAt = :now
        WHERE externalRef = :externalRef AND isUnread = 1
        """,
    )
    suspend fun markReadByExternalRef(externalRef: String, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE memory_documents
        SET isUnread = 0, updatedAt = :now
        WHERE kind = :kind AND isUnread = 1 AND isActive = 1
        """,
    )
    suspend fun markAllUnreadByKind(kind: String, now: Long = System.currentTimeMillis()): Int
}
