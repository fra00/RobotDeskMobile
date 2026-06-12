package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTopicMatcherTest {

    private fun entity(value: String, id: Long = 1L) = MemoryItemEntity(
        id = id,
        category = MemoryCategory.FACT,
        value = value,
        confidence = 0.8f,
        createdAt = 0L,
        updatedAt = 0L,
        sourceMessageId = 0L,
    )

    @Test
    fun `dog topic matches brina memory`() {
        val score = MemoryTopicMatcher.score("cane Brina", "L'utente ha un cane di nome Brina")
        assertTrue(score >= MemoryTopicMatcher.MIN_FORGET_SCORE)
    }

    @Test
    fun `rank returns multiple related memories`() {
        val items = listOf(
            entity("L'utente ha un cane di nome Brina", 1L),
            entity("Brina va dal veterinario ogni mese", 2L),
            entity("L'utente preferisce il caffè", 3L),
        )
        val ranked = MemoryTopicMatcher.rank("cane brina", items)
        assertTrue(ranked.size >= 2)
        assertTrue(ranked.none { it.item.id == 3L })
    }

    @Test
    fun `unrelated topic scores low`() {
        val score = MemoryTopicMatcher.score("gatto", "L'utente ha un cane di nome Brina")
        assertTrue(score < MemoryTopicMatcher.MIN_FORGET_SCORE)
    }
}
