package com.example.mydeskrobot.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getAllActive(): List<MemoryItemEntity>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        AND category = :category
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getByCategory(category: MemoryCategory, limit: Int): List<MemoryItemEntity>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        AND (
            value LIKE '%' || :query || '%'
        )
        ORDER BY confidence DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchByQuery(query: String, limit: Int): List<MemoryItemEntity>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        AND category = :category
        AND LOWER(value) = LOWER(:value)
        LIMIT 1
        """
    )
    suspend fun findExact(category: MemoryCategory, value: String): MemoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MemoryItemEntity): Long

    @Query(
        """
        UPDATE memory_items
        SET isDeleted = 1, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun softDeleteById(id: Long, now: Long)

    @Query(
        """
        UPDATE memory_items
        SET isDeleted = 1, updatedAt = :now
        WHERE isDeleted = 0 AND value LIKE '%' || :query || '%'
        """
    )
    suspend fun softDeleteByText(query: String, now: Long): Int

    @Query("DELETE FROM memory_items")
    suspend fun clearAll()

    @Query(
        """
        UPDATE memory_items
        SET lastUsedAt = :usedAt
        WHERE id IN (:ids)
        """
    )
    suspend fun markUsed(ids: List<Long>, usedAt: Long)

    @Query("SELECT COUNT(*) FROM memory_items WHERE isDeleted = 0")
    suspend fun countActive(): Int

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        ORDER BY confidence ASC, lastUsedAt ASC, updatedAt ASC
        LIMIT :limit
        """
    )
    suspend fun lowPriorityForPruning(limit: Int): List<MemoryItemEntity>
}
