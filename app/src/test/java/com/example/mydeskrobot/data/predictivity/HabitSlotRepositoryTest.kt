package com.example.mydeskrobot.data.predictivity

import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.predictivity.HabitSlotEligibility
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitSlotRepositoryTest {

    @Test
    fun `upsert and list eligible round trip`() = runTest {
        val repo = HabitSlotRepository(UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao()))
        val slot = HabitSlot(
            slotKey = "passeggiata_cane|510",
            canonicalLabel = "passeggiata_cane",
            displayLabel = "Passeggiata cane",
            typicalTimeMinutes = 510,
            hitCount = 5,
            confidence = 0.75f,
            lastHitDayKey = "2026-06-10",
            rawLabels = setOf("passeggiata cane"),
        )
        repo.upsert(slot)

        val loaded = repo.findBySlotKey(slot.slotKey)
        assertNotNull(loaded)
        assertEquals(5, loaded!!.hitCount)

        val eligible = repo.listEligibleForDeviation()
        assertEquals(1, eligible.size)
        assertTrue(HabitSlotEligibility.isEligibleForDeviation(eligible.first()))
    }
}
