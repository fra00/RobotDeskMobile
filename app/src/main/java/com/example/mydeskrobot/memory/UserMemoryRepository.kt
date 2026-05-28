package com.example.mydeskrobot.memory

import android.content.Context
import androidx.room.Room
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryDao
import com.example.mydeskrobot.memory.db.MemoryDatabase
import com.example.mydeskrobot.memory.db.MemoryItemEntity

class UserMemoryRepository(
    private val dao: MemoryDao,
) {
    suspend fun upsert(
        category: MemoryCategory,
        value: String,
        confidence: Float,
        sourceMessageId: Long,
    ): Long {
        val normalized = value.trim()
        if (normalized.isBlank()) return -1L
        val now = System.currentTimeMillis()
        val existing = dao.findExact(category, normalized)
        val item = if (existing != null) {
            existing.copy(
                confidence = maxOf(existing.confidence, confidence),
                updatedAt = now,
                sourceMessageId = sourceMessageId,
                isDeleted = false,
            )
        } else {
            MemoryItemEntity(
                category = category,
                value = normalized,
                confidence = confidence.coerceIn(0f, 1f),
                createdAt = now,
                updatedAt = now,
                sourceMessageId = sourceMessageId,
            )
        }
        return dao.upsert(item)
    }

    suspend fun getCoreIdentity(limit: Int = 2): List<MemoryItemEntity> =
        dao.getByCategory(MemoryCategory.IDENTITY, limit)

    suspend fun searchRelevant(query: String, limit: Int): List<MemoryItemEntity> =
        dao.searchByQuery(query.trim(), limit)

    suspend fun getAllActive(): List<MemoryItemEntity> = dao.getAllActive()

    suspend fun markUsed(items: List<MemoryItemEntity>) {
        if (items.isEmpty()) return
        dao.markUsed(items.map { it.id }, System.currentTimeMillis())
    }

    suspend fun forgetByText(query: String): Int =
        dao.softDeleteByText(query.trim(), System.currentTimeMillis())

    suspend fun resetMemory() {
        dao.clearAll()
    }

    suspend fun pruneIfNeeded(maxItems: Int): Int {
        val active = dao.countActive()
        if (active <= maxItems) return 0
        val toDelete = active - maxItems
        val lowPriority = dao.lowPriorityForPruning(toDelete)
        val now = System.currentTimeMillis()
        lowPriority.forEach { dao.softDeleteById(it.id, now) }
        return lowPriority.size
    }

    suspend fun reorganize(): Int {
        val active = dao.getAllActive()
        val grouped = active.groupBy { "${it.category}:${it.value.trim().lowercase()}" }
        var removed = 0
        val now = System.currentTimeMillis()
        grouped.values.forEach { group ->
            if (group.size <= 1) return@forEach
            val sorted = group.sortedWith(
                compareByDescending<MemoryItemEntity> { it.confidence }
                    .thenByDescending { it.updatedAt },
            )
            sorted.drop(1).forEach {
                dao.softDeleteById(it.id, now)
                removed++
            }
        }
        return removed
    }

    companion object {
        fun create(context: Context): UserMemoryRepository {
            val db = Room.databaseBuilder(
                context.applicationContext,
                MemoryDatabase::class.java,
                "user_memory.db",
            ).build()
            return UserMemoryRepository(db.memoryDao())
        }
    }
}
