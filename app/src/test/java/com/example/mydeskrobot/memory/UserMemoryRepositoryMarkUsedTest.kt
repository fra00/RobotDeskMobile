package com.example.mydeskrobot.memory

import com.example.mydeskrobot.integration.memory.FakeMemoryDao
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMemoryRepositoryMarkUsedTest {

    @Test
    fun markUsed_incrementsUseCountAndUpdatesLastUsedAt() = runTest {
        val entity = MemoryItemEntity(
            id = 1L,
            category = MemoryCategory.FACT,
            value = "L'utente ha un cane di nome Brina",
            confidence = 0.9f,
            createdAt = 100L,
            updatedAt = 100L,
            lastUsedAt = 0L,
            useCount = 2,
            sourceMessageId = 0L,
        )
        val dao = FakeMemoryDao(listOf(entity))
        val repository = UserMemoryRepository.createForTest(dao)

        repository.markUsed(listOf(entity))

        val updated = dao.getAllActive(now = Long.MAX_VALUE).single()
        assertEquals(3, updated.useCount)
        assertEquals(true, updated.lastUsedAt > 0L)
    }
}
