package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.consolidate.ConsolidatedMemoryLine
import com.example.mydeskrobot.memory.db.MemoryCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedMemoryConsolidationTest {

    @Test
    fun replaceUserFacingWithConsolidated_preserves_unchanged_rows() = runTest {
        val dao = FakeMemoryDocumentDao(
            listOf(
                userFact(10L, "Il cane si chiama Brina", MemoryCategory.FACT, useCount = 7),
                userFact(11L, "Orari: lunedi 9-13", MemoryCategory.ROUTINE),
                userFact(12L, "Orari: martedi 9-18", MemoryCategory.ROUTINE),
            ),
        )
        val repository = UnifiedMemoryRepository.createForTest(dao)

        val after = repository.replaceUserFacingWithConsolidated(
            listOf(
                ConsolidatedMemoryLine(
                    category = MemoryCategory.ROUTINE,
                    value = "L'utente lavora lunedi 9-13 e martedi 9-18.",
                ),
                ConsolidatedMemoryLine(
                    category = MemoryCategory.FACT,
                    value = "Il cane si chiama Brina",
                ),
            ),
        )

        assertEquals(2, after)
        val brina = dao.getById(10L)!!
        assertTrue(brina.isActive)
        assertEquals(7, brina.useCount)
        assertEquals("Il cane si chiama Brina", brina.value)
        assertTrue(dao.getById(11L)!!.isActive.not() || dao.getById(12L)!!.isActive.not())
    }

    @Test
    fun replaceUserFacingWithConsolidated_leaves_pinned_rows_untouched() = runTest {
        val dao = FakeMemoryDocumentDao(
            listOf(
                userFact(20L, "L'utente si chiama Francesco", MemoryCategory.IDENTITY, isPinned = true),
                userFact(21L, "L'utente ama il cinema", MemoryCategory.PREFERENCE),
            ),
        )
        val repository = UnifiedMemoryRepository.createForTest(dao)

        repository.replaceUserFacingWithConsolidated(
            listOf(
                ConsolidatedMemoryLine(
                    category = MemoryCategory.PREFERENCE,
                    value = "L'utente ama il cinema e il teatro",
                ),
            ),
        )

        val francesco = dao.getById(20L)!!
        assertTrue(francesco.isActive)
        assertTrue(francesco.isPinned)
        assertEquals("L'utente si chiama Francesco", francesco.value)
    }

    private fun userFact(
        id: Long,
        value: String,
        category: MemoryCategory,
        useCount: Int = 0,
        isPinned: Boolean = false,
    ) = com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity(
        id = id,
        value = value,
        kind = MemoryDocumentKind.USER_FACT.name,
        category = category.name,
        source = MemoryDocumentSource.TOOL.name,
        confidence = 0.9f,
        useCount = useCount,
        createdAt = 1L,
        updatedAt = 1L,
        isPinned = isPinned,
    )
}
