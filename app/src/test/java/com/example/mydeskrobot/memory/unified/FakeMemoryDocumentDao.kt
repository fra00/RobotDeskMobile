package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.unified.db.MemoryDocumentDao
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity

class FakeMemoryDocumentDao(
    initial: List<MemoryDocumentEntity> = emptyList(),
) : MemoryDocumentDao {

    private val items = initial.map { it.copy() }.toMutableList()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun upsert(entity: MemoryDocumentEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        items.removeAll { it.id == id }
        items += entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: MemoryDocumentEntity) {
        val index = items.indexOfFirst { it.id == entity.id }
        if (index >= 0) items[index] = entity
    }

    override suspend fun getById(id: Long): MemoryDocumentEntity? =
        items.firstOrNull { it.id == id }

    override suspend fun getByExternalRef(externalRef: String): MemoryDocumentEntity? =
        items.firstOrNull { it.externalRef == externalRef }

    override suspend fun getAllActive(now: Long): List<MemoryDocumentEntity> =
        items.filter { it.isActive && (it.expiresAt == null || it.expiresAt > now) }

    override suspend fun countActive(): Int =
        items.count { it.isActive }

    override suspend fun deactivateById(id: Long, now: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(isActive = false, updatedAt = now)
        }
    }

    override suspend fun deactivateByExternalRef(externalRef: String, now: Long) {
        items.forEachIndexed { index, item ->
            if (item.externalRef == externalRef && item.isActive) {
                items[index] = item.copy(isActive = false, updatedAt = now)
            }
        }
    }

    override suspend fun markUsed(ids: List<Long>, now: Long) {
        items.forEachIndexed { index, item ->
            if (item.id in ids) {
                items[index] = item.copy(
                    useCount = item.useCount + 1,
                    lastUsedAt = now,
                )
            }
        }
    }

    override suspend fun getActiveByKind(
        kind: String,
        limit: Int,
        now: Long,
    ): List<MemoryDocumentEntity> =
        getAllActive(now).filter { it.kind == kind }.take(limit)

    override suspend fun getActiveByKindAndScheduledDay(
        kind: String,
        scheduledDayKey: String,
        now: Long,
    ): List<MemoryDocumentEntity> =
        getAllActive(now).filter {
            it.kind == kind && it.scheduledDayKey == scheduledDayKey
        }

    override suspend fun getUnreadByKind(
        kind: String,
        limit: Int,
        now: Long,
    ): List<MemoryDocumentEntity> =
        getAllActive(now).filter { it.kind == kind && it.isUnread }.take(limit)

    override suspend fun markReadByExternalRef(externalRef: String, now: Long) {
        items.forEachIndexed { index, item ->
            if (item.externalRef == externalRef && item.isUnread) {
                items[index] = item.copy(isUnread = false, updatedAt = now)
            }
        }
    }

    override suspend fun markAllUnreadByKind(kind: String, now: Long): Int {
        var count = 0
        items.forEachIndexed { index, item ->
            if (item.kind == kind && item.isUnread && item.isActive) {
                items[index] = item.copy(isUnread = false, updatedAt = now)
                count++
            }
        }
        return count
    }
}
