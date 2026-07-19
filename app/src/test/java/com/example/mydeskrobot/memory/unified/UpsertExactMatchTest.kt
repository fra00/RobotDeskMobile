package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.db.MemoryCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpsertExactMatchTest {

    @Test
    fun upsert_exact_same_text_updates_single_row() = runTest {
        val repository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        repository.upsertUserFacingFact(
            category = MemoryCategory.IDENTITY,
            value = "L'utente si chiama Francesco",
            confidence = 0.8f,
            source = MemoryDocumentSource.TOOL,
        )
        repository.upsertUserFacingFact(
            category = MemoryCategory.IDENTITY,
            value = "L'utente si chiama Francesco",
            confidence = 0.95f,
            source = MemoryDocumentSource.TOOL,
        )

        val active = repository.getUserFacingActiveDocuments()
        assertEquals(1, active.size)
        assertEquals(0.95f, active.first().confidence, 0.001f)
    }

    @Test
    fun upsert_paraphrase_inserts_second_row() = runTest {
        val repository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        repository.upsertUserFacingFact(
            category = MemoryCategory.IDENTITY,
            value = "L'utente si chiama Francesco",
            confidence = 0.8f,
            source = MemoryDocumentSource.TOOL,
        )
        repository.upsertUserFacingFact(
            category = MemoryCategory.IDENTITY,
            value = "The user's name is Francesco",
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )

        assertEquals(2, repository.getUserFacingActiveDocuments().size)
    }

    @Test
    fun upsert_exact_match_preserves_or_merges_pinned() = runTest {
        val repository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        repository.upsertUserFacingFact(
            category = MemoryCategory.FACT,
            value = "L'utente è allergico alle noci",
            confidence = 0.8f,
            source = MemoryDocumentSource.TOOL,
            isPinned = false,
        )
        repository.upsertUserFacingFact(
            category = MemoryCategory.FACT,
            value = "L'utente è allergico alle noci",
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
            isPinned = true,
        )

        val row = repository.getUserFacingActiveDocuments().single()
        assertTrue(row.isPinned)
        assertEquals(0.9f, row.confidence, 0.001f)
    }
}
