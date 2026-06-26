package com.example.mydeskrobot.memory.consolidate

import com.example.mydeskrobot.memory.db.MemoryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryConsolidationCoverageTest {

    @Test
    fun appendUncoveredInputLines_reappends_dropped_work_facts() {
        val input = listOf(
            MemoryConsolidationCoverage.InputLine(
                MemoryCategory.IDENTITY,
                "L'utente è uno sviluppatore web per TeamSystem",
            ),
            MemoryConsolidationCoverage.InputLine(
                MemoryCategory.FACT,
                "Il cane si chiama Brina",
            ),
            MemoryConsolidationCoverage.InputLine(
                MemoryCategory.FACT,
                "L'utente conosce bene il linguaggio C#",
            ),
        )
        val llmOutput = listOf(
            ConsolidatedMemoryLine(MemoryCategory.FACT, "Il cane si chiama Brina."),
        )

        val safe = MemoryConsolidationCoverage.appendUncoveredInputLines(input, llmOutput)

        assertEquals(3, safe.size)
        assertTrue(safe.any { it.value.contains("sviluppatore", ignoreCase = true) })
        assertTrue(safe.any { it.value.contains("C#", ignoreCase = true) })
        assertTrue(safe.any { it.value.contains("Brina", ignoreCase = true) })
    }

    @Test
    fun appendUncoveredInputLines_keeps_merged_line_without_duplicate() {
        val input = listOf(
            MemoryConsolidationCoverage.InputLine(
                MemoryCategory.IDENTITY,
                "L'utente è sviluppatore",
            ),
            MemoryConsolidationCoverage.InputLine(
                MemoryCategory.IDENTITY,
                "L'utente lavora in TeamSystem",
            ),
        )
        val llmOutput = listOf(
            ConsolidatedMemoryLine(
                MemoryCategory.IDENTITY,
                "L'utente è sviluppatore web in TeamSystem",
            ),
        )

        val safe = MemoryConsolidationCoverage.appendUncoveredInputLines(input, llmOutput)

        assertEquals(1, safe.size)
        assertTrue(safe.first().value.contains("TeamSystem"))
    }
}
