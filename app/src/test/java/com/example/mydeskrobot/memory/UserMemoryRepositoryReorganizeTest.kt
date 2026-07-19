package com.example.mydeskrobot.memory

import com.example.mydeskrobot.integration.memory.FakeMemoryDao
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryRepositoryReorganizeTest {

    @Test
    fun reorganize_underCap_doesNotRemoveDuplicates() = runBlocking {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, "L'utente si chiama Francesco", MemoryCategory.IDENTITY, 0.9f),
                entity(2L, "The user's name is Francesco", MemoryCategory.IDENTITY, 0.7f),
                entity(3L, "L'utente ama il cinema", MemoryCategory.PREFERENCE, 0.8f),
            ),
        )
        val repository = UserMemoryRepository.createForTest(dao)

        val removed = repository.reorganize()

        assertEquals(0, removed)
        assertEquals(3, repository.getAllActive().size)
    }

    @Test
    fun reorganize_overCap_prunesLeastUsedRows() = runBlocking {
        val max = UnifiedMemoryRepository.USER_FACING_MAX_ITEMS
        val items = buildList {
            repeat(max) { index ->
                add(
                    entity(
                        id = index + 1L,
                        value = "Fatto archivio $index",
                        category = MemoryCategory.FACT,
                        confidence = 0.9f,
                        useCount = 3,
                    ),
                )
            }
            add(
                entity(
                    id = max + 1L,
                    value = "Fatto raramente richiamato",
                    category = MemoryCategory.FACT,
                    confidence = 0.9f,
                    useCount = 0,
                ),
            )
        }
        val repository = UserMemoryRepository.createForTest(FakeMemoryDao(items))

        val removed = repository.reorganize()

        assertEquals(1, removed)
        assertEquals(max, repository.getAllActive().size)
        assertTrue(repository.getAllActive().none { it.value.contains("raramente") })
    }

    @Test
    fun upsert_insertsParafraseAsSeparateRow() = runBlocking {
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

        assertEquals(2, repository.getAllActive().size)
    }

    private fun entity(
        id: Long,
        value: String,
        category: MemoryCategory,
        confidence: Float,
        useCount: Int = 0,
    ) = MemoryItemEntity(
        id = id,
        category = category,
        value = value,
        confidence = confidence,
        useCount = useCount,
        createdAt = 1L,
        updatedAt = 1L,
        sourceMessageId = 0L,
    )
}
