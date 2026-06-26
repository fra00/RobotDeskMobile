package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedMemoryWriteTest {

    @Test
    fun upsertUserFacingFact_dedupes_exact_duplicates() = runTest {
        val repository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        val first = repository.upsertUserFacingFact(
            category = MemoryCategory.FACT,
            value = "L'utente lavora con C#",
            confidence = 0.8f,
            source = MemoryDocumentSource.TOOL,
        )
        val second = repository.upsertUserFacingFact(
            category = MemoryCategory.FACT,
            value = "L'utente lavora con C#",
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )
        assertTrue(first > 0L)
        assertEquals(first, second)
        assertEquals(1, repository.getUserFacingActiveDocuments().size)
        assertEquals(0.9f, repository.getUserFacingActiveDocuments().first().confidence, 0.001f)
    }


    @Test
    fun forgetByTopic_soft_deletes_matching_docs() = runTest {
        val repository = UnifiedMemoryRepository.createForTest(
            FakeMemoryDocumentDao(
                listOf(
                    userFact(1L, "Il cane si chiama Brina", MemoryCategory.FACT),
                    userFact(2L, "L'utente lavora in TeamSystem", MemoryCategory.IDENTITY),
                ),
            ),
        )
        val result = repository.forgetByTopic("cane Brina")
        assertEquals(1, result.deletedCount)
        assertEquals(1, repository.getUserFacingActiveDocuments().size)
    }

    private fun userFact(id: Long, value: String, category: MemoryCategory) =
        MemoryDocumentEntity(
            id = id,
            value = value,
            kind = MemoryDocumentKind.USER_FACT.name,
            category = category.name,
            source = MemoryDocumentSource.TOOL.name,
            confidence = 0.9f,
            createdAt = 1L,
            updatedAt = 1L,
        )
}
