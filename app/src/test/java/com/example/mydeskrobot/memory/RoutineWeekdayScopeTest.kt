package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.consolidate.ConsolidatedMemoryLine
import com.example.mydeskrobot.memory.consolidate.MemoryConsolidationCoverage
import com.example.mydeskrobot.memory.db.MemoryCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineWeekdayScopeTest {

    @Test
    fun `weekday indices parse range and single day`() {
        assertTrue(RoutineWeekdayScope.weekdayIndices("Dal lunedì al giovedì lavora 9-18").contains(1))
        assertTrue(RoutineWeekdayScope.weekdayIndices("Dal lunedì al giovedì lavora 9-18").contains(4))
        assertTrue(RoutineWeekdayScope.weekdayIndices("Il venerdì lavora 9-13").contains(5))
    }

    @Test
    fun `distinct weekday scopes are not duplicates`() {
        val lunGio = "Dal lunedì al giovedì lavora dalle 9 alle 18"
        val venerdi = "Il venerdì lavora dalle 9 alle 13"

        assertTrue(RoutineWeekdayScope.hasDistinctWeekdayScope(lunGio, venerdi))
        assertFalse(
            MemoryDuplicateDetector.areDuplicates(lunGio, venerdi, MemoryCategory.ROUTINE),
        )
    }

    @Test
    fun `consolidation coverage does not drop other weekday routine`() {
        val input = "Dal lunedì al giovedì lavora dalle 9 alle 18"
        val merged = ConsolidatedMemoryLine(
            category = MemoryCategory.ROUTINE,
            value = "Il venerdì lavora dalle 9 alle 13",
        )

        assertFalse(
            MemoryConsolidationCoverage.isInputCoveredByLine(
                MemoryCategory.ROUTINE,
                input,
                merged,
            ),
        )
    }
}
