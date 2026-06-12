package com.example.mydeskrobot.memory

import com.example.mydeskrobot.integration.memory.FakeMemoryDao
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryRepositoryReorganizeTest {

    @Test
    fun reorganize_removesSemanticDuplicates() = runBlocking {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, "L'utente si chiama Francesco", MemoryCategory.IDENTITY, 0.9f),
                entity(2L, "The user's name is Francesco", MemoryCategory.IDENTITY, 0.7f),
                entity(3L, "L'utente ama il cinema", MemoryCategory.PREFERENCE, 0.8f),
            ),
        )
        val repository = UserMemoryRepository.createForTest(dao)

        val removed = repository.reorganize()

        assertEquals(1, removed)
        val active = repository.getAllActive()
        assertEquals(2, active.size)
        assertTrue(active.any { it.value.contains("Francesco", ignoreCase = true) })
    }

    @Test
    fun upsert_mergesSemanticDuplicateInsteadOfInserting() = runBlocking {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, "L'utente si chiama Francesco", MemoryCategory.IDENTITY, 0.8f),
            ),
        )
        val repository = UserMemoryRepository.createForTest(dao)

        repository.upsert(
            category = MemoryCategory.IDENTITY,
            value = "The user's name is Francesco",
            confidence = 0.9f,
            sourceMessageId = 99L,
        )

        assertEquals(1, repository.getAllActive().size)
        assertEquals(0.9f, repository.getAllActive().first().confidence, 0.001f)
    }

    private fun entity(
        id: Long,
        value: String,
        category: MemoryCategory,
        confidence: Float,
    ) = MemoryItemEntity(
        id = id,
        category = category,
        value = value,
        confidence = confidence,
        createdAt = 1L,
        updatedAt = 1L,
        sourceMessageId = 0L,
    )
}
