package com.example.mydeskrobot.memory.consolidate

import com.example.mydeskrobot.memory.db.MemoryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryConsolidationApplicatorTest {

    @Test
    fun plan_keeps_unchanged_row_with_same_id_and_use_count() {
        val active = listOf(
            row(10L, MemoryCategory.FACT, "Il cane si chiama Brina", useCount = 5),
            row(11L, MemoryCategory.PREFERENCE, "Ama guardare il MotoGP", useCount = 2),
        )
        val consolidated = listOf(
            ConsolidatedMemoryLine(MemoryCategory.FACT, "Il cane si chiama Brina"),
            ConsolidatedMemoryLine(MemoryCategory.PREFERENCE, "Ama guardare il MotoGP"),
        )

        val plan = MemoryConsolidationApplicator.plan(active, consolidated)

        assertEquals(setOf(10L, 11L), plan.unchangedIds)
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deactivateIds.isEmpty())
        assertTrue(plan.inserts.isEmpty())
    }

    @Test
    fun plan_merges_duplicates_and_sums_use_count() {
        val active = listOf(
            row(1L, MemoryCategory.ROUTINE, "Lun 9-13", useCount = 3),
            row(2L, MemoryCategory.ROUTINE, "Lunedi 9-13", useCount = 2),
        )
        val consolidated = listOf(
            ConsolidatedMemoryLine(
                MemoryCategory.ROUTINE,
                "L'utente lavora il lunedi 9-13",
            ),
        )

        val plan = MemoryConsolidationApplicator.plan(active, consolidated)

        assertEquals(setOf(2L), plan.deactivateIds)
        assertEquals(1, plan.updates.size)
        assertEquals(5, plan.updates[1L]!!.useCount)
        assertTrue(plan.updates[1L]!!.value.contains("lunedi", ignoreCase = true))
    }

    @Test
    fun plan_leaves_unmatched_active_rows_out_of_plan() {
        val active = listOf(
            row(1L, MemoryCategory.FACT, "Il cane si chiama Brina", useCount = 1),
            row(2L, MemoryCategory.PREFERENCE, "Ama il MotoGP", useCount = 4),
        )
        val consolidated = listOf(
            ConsolidatedMemoryLine(MemoryCategory.FACT, "Il cane si chiama Brina"),
        )

        val plan = MemoryConsolidationApplicator.plan(active, consolidated)

        assertEquals(setOf(1L), plan.unchangedIds)
        assertTrue(2L !in plan.deactivateIds)
        assertTrue(2L !in plan.updates.keys)
    }

    private fun row(
        id: Long,
        category: MemoryCategory,
        value: String,
        useCount: Int = 0,
    ) = MemoryConsolidationApplicator.MemoryRow(
        id = id,
        category = category,
        value = value,
        useCount = useCount,
        lastUsedAt = 100L,
        updatedAt = 100L,
        createdAt = 50L,
    )
}
