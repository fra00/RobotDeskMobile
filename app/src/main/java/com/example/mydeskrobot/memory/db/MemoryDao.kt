package com.example.mydeskrobot.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface MemoryDao {

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        AND (expiresAt IS NULL OR expiresAt > :now)
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getAllActive(now: Long = System.currentTimeMillis()): List<MemoryItemEntity>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        AND category IN (:categories)
        AND (expiresAt IS NULL OR expiresAt > :now)
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getUserFacingActive(
        categories: List<MemoryCategory>,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryItemEntity>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        AND category = :category
        AND (expiresAt IS NULL OR expiresAt > :now)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getByCategory(
        category: MemoryCategory,
        limit: Int,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryItemEntity>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        AND category = :category
        AND (expiresAt IS NULL OR expiresAt > :now)
        AND (
            value LIKE '%' || :query || '%'
        )
        ORDER BY confidence DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchByQuery(
        category: MemoryCategory,
        query: String,
        limit: Int,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryItemEntity>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        AND (expiresAt IS NULL OR expiresAt > :now)
        AND (
            value LIKE '%' || :query || '%'
        )
        ORDER BY confidence DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchByQuery(query: String, limit: Int, now: Long = System.currentTimeMillis()): List<MemoryItemEntity>

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

    @Query(
        """
        SELECT * FROM memory_items
        WHERE id = :id AND isDeleted = 0
        LIMIT 1
        """
    )
    suspend fun findActiveById(id: Long): MemoryItemEntity?

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

    @Query(
        """
        UPDATE memory_items
        SET value = :value, updatedAt = :now
        WHERE id = :id AND isDeleted = 0
        """
    )
    suspend fun updateValue(id: Long, value: String, now: Long): Int

    @Query("DELETE FROM memory_items")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM memory_items
        WHERE category IN (:categories)
        """
    )
    suspend fun clearByCategories(categories: List<MemoryCategory>)

    @Query(
        """
        UPDATE memory_items
        SET lastUsedAt = :usedAt, useCount = useCount + 1
        WHERE id IN (:ids)
        """
    )
    suspend fun markUsed(ids: List<Long>, usedAt: Long)

    @Query(
        """
        SELECT COUNT(*) FROM memory_items
        WHERE isDeleted = 0
        AND (expiresAt IS NULL OR expiresAt > :now)
        """
    )
    suspend fun countActive(now: Long = System.currentTimeMillis()): Int

    @Query(
        """
        SELECT COUNT(*) FROM memory_items
        WHERE isDeleted = 0
        AND category = :category
        AND (expiresAt IS NULL OR expiresAt > :now)
        """
    )
    suspend fun countActiveByCategory(
        category: MemoryCategory,
        now: Long = System.currentTimeMillis(),
    ): Int

    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
        AND category NOT IN (:excludeCategories)
        AND (expiresAt IS NULL OR expiresAt > :now)
        ORDER BY useCount ASC, lastUsedAt ASC, confidence ASC, updatedAt ASC
        LIMIT :limit
        """
    )
    suspend fun lowPriorityForPruning(
        excludeCategories: List<MemoryCategory>,
        limit: Int,
        now: Long = System.currentTimeMillis(),
    ): List<MemoryItemEntity>

    @Query(
        """
        UPDATE memory_items
        SET isDeleted = 1, updatedAt = :now
        WHERE isDeleted = 0
        AND expiresAt IS NOT NULL
        AND expiresAt <= :now
        """
    )
    suspend fun softDeleteExpired(now: Long): Int

    @Query(
        """
        UPDATE memory_items
        SET isDeleted = 1, updatedAt = :now
        WHERE isDeleted = 0
        AND category IN (:categories)
        AND (expiresAt IS NULL OR expiresAt > :now)
        """,
    )
    suspend fun softDeleteActiveInCategories(
        categories: List<MemoryCategory>,
        now: Long = System.currentTimeMillis(),
    )

    @Transaction
    suspend fun replaceUserFacingMemories(
        categories: List<MemoryCategory>,
        newItems: List<MemoryItemEntity>,
        now: Long = System.currentTimeMillis(),
    ) {
        softDeleteActiveInCategories(categories, now)
        newItems.forEach { upsert(it) }
    }
}
