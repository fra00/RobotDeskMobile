package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySearchScorerTest {

    @Test
    fun rank_finds_related_work_hours() {
        val doc = MemoryDocumentEntity(
            id = 1L,
            value = "Il venerdì lavora dalle 9 alle 13",
            kind = MemoryDocumentKind.USER_FACT.name,
            category = "ROUTINE",
            source = MemoryDocumentSource.TOOL.name,
            confidence = 0.9f,
            createdAt = 1L,
            updatedAt = 1L,
        )

        val ranked = MemorySearchScorer.rank(
            query = "quando lavoro il venerdì",
            documents = listOf(doc),
            limit = 5,
        )

        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.first().score >= MemorySearchScorer.DEFAULT_MIN_SCORE)
    }
}
