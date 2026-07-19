package com.example.mydeskrobot.integration.memory

import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryDao
import com.example.mydeskrobot.memory.db.MemoryItemEntity

class FakeMemoryDao(
    initial: List<MemoryItemEntity> = emptyList(),
) : MemoryDao {

    private val items = initial.map { it.copy() }.toMutableList()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun isActive(item: MemoryItemEntity, now: Long): Boolean =
        !item.isDeleted && (item.expiresAt == null || item.expiresAt > now)

    override suspend fun getAllActive(now: Long): List<MemoryItemEntity> =
        items.filter { isActive(it, now) }.sortedByDescending { it.updatedAt }

    override suspend fun getUserFacingActive(
        categories: List<MemoryCategory>,
        now: Long,
    ): List<MemoryItemEntity> =
        getAllActive(now).filter { it.category in categories }

    override suspend fun getByCategory(
        category: MemoryCategory,
        limit: Int,
        now: Long,
    ): List<MemoryItemEntity> =
        getAllActive(now).filter { it.category == category }.take(limit)

    override suspend fun searchByQuery(
        category: MemoryCategory,
        query: String,
        limit: Int,
        now: Long,
    ): List<MemoryItemEntity> =
        getAllActive(now)
            .filter { it.category == category && it.value.contains(query, ignoreCase = true) }
            .take(limit)

    override suspend fun searchByQuery(query: String, limit: Int, now: Long): List<MemoryItemEntity> =
        getAllActive(now)
            .filter { it.value.contains(query, ignoreCase = true) }
            .take(limit)

    override suspend fun findExact(category: MemoryCategory, value: String): MemoryItemEntity? =
        items.firstOrNull {
            !it.isDeleted && it.category == category &&
                it.value.equals(value, ignoreCase = true)
        }

    override suspend fun findActiveById(id: Long): MemoryItemEntity? =
        items.firstOrNull { !it.isDeleted && it.id == id }

    override suspend fun upsert(item: MemoryItemEntity): Long {
        val id = if (item.id == 0L) nextId++ else item.id
        items.removeAll { it.id == id }
        items += item.copy(id = id)
        return id
    }

    override suspend fun softDeleteById(id: Long, now: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(isDeleted = true, updatedAt = now)
        }
    }

    override suspend fun softDeleteByText(query: String, now: Long): Int {
        var count = 0
        items.forEachIndexed { index, item ->
            if (!item.isDeleted && item.value.contains(query, ignoreCase = true)) {
                items[index] = item.copy(isDeleted = true, updatedAt = now)
                count++
            }
        }
        return count
    }

    override suspend fun updateValue(id: Long, value: String, now: Long): Int {
        val index = items.indexOfFirst { it.id == id && !it.isDeleted }
        if (index < 0) return 0
        items[index] = items[index].copy(value = value, updatedAt = now)
        return 1
    }

    override suspend fun clearAll() {
        items.clear()
    }

    override suspend fun clearByCategories(categories: List<MemoryCategory>) {
        items.removeAll { it.category in categories }
    }

    override suspend fun markUsed(ids: List<Long>, usedAt: Long) {
        items.forEachIndexed { index, item ->
            if (item.id in ids) {
                items[index] = item.copy(
                    lastUsedAt = usedAt,
                    useCount = item.useCount + 1,
                )
            }
        }
    }

    override suspend fun countActive(now: Long): Int = getAllActive(now).size

    override suspend fun countActiveByCategory(category: MemoryCategory, now: Long): Int =
        getAllActive(now).count { it.category == category }

    override suspend fun lowPriorityForPruning(
        excludeCategories: List<MemoryCategory>,
        limit: Int,
        now: Long,
    ): List<MemoryItemEntity> =
        getAllActive(now)
            .filter { it.category !in excludeCategories }
            .sortedWith(
                compareBy(
                    { it.useCount },
                    { it.lastUsedAt },
                    { it.confidence },
                    { it.updatedAt },
                ),
            )
            .take(limit)

    override suspend fun softDeleteExpired(now: Long): Int {
        var count = 0
        items.forEachIndexed { index, item ->
            if (!item.isDeleted && item.expiresAt != null && item.expiresAt <= now) {
                items[index] = item.copy(isDeleted = true, updatedAt = now)
                count++
            }
        }
        return count
    }

    override suspend fun softDeleteActiveInCategories(
        categories: List<MemoryCategory>,
        now: Long,
    ) {
        items.forEachIndexed { index, item ->
            if (
                !item.isDeleted &&
                item.category in categories &&
                (item.expiresAt == null || item.expiresAt > now)
            ) {
                items[index] = item.copy(isDeleted = true, updatedAt = now)
            }
        }
    }

    override suspend fun replaceUserFacingMemories(
        categories: List<MemoryCategory>,
        newItems: List<MemoryItemEntity>,
        now: Long,
    ) {
        softDeleteActiveInCategories(categories, now)
        newItems.forEach { upsert(it) }
    }
}
