package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.data.predictivity.HabitSlotCodec
import com.example.mydeskrobot.data.predictivity.HabitSlotRepository
import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.proactive.ProactivityConstants
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteMemoryToolTest {

    @Test
    fun `slotKeyFromExternalRef decodes habit slot prefix`() {
        assertEquals(
            "passeggiata_cane|510",
            DeleteMemoryTool.slotKeyFromExternalRef("habit_slot:passeggiata_cane|510"),
        )
        assertNull(DeleteMemoryTool.slotKeyFromExternalRef("other:ref"))
        assertNull(DeleteMemoryTool.slotKeyFromExternalRef("habit_slot:"))
    }

    @Test
    fun `delete by memory id removes linked habit slot`() = runTest {
        val unified = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        val habitRepo = HabitSlotRepository(unified)
        val slot = HabitSlot(
            slotKey = "passeggiata_cane|510",
            canonicalLabel = "passeggiata_cane",
            displayLabel = "Passeggiata cane",
            typicalTimeMinutes = 510,
            hitCount = 5,
            confidence = 0.75f,
            rawLabels = setOf("passeggiata cane"),
        )
        habitRepo.upsert(slot)

        val externalRef = HabitSlotCodec.externalRef(slot.slotKey)
        assertTrue(externalRef.startsWith(ProactivityConstants.HABIT_SLOT_EXTERNAL_REF_PREFIX))
        val memoryId = unified.getByExternalRef(externalRef)!!.id

        val tool = DeleteMemoryTool(unified, habitRepo)
        val result = tool.execute(
            ToolInvocation(name = "delete_memory", params = mapOf("memory_id" to memoryId)),
        )

        assertTrue(result is ToolResult.Success)
        assertTrue(habitRepo.listAll().isEmpty())
        val doc = unified.getByExternalRef(externalRef)
        assertTrue(doc == null || !doc.isActive)
    }

    @Test
    fun `delete by query removes habit slots matching canonical label`() = runTest {
        val unified = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        val habitRepo = HabitSlotRepository(unified)
        habitRepo.upsert(
            HabitSlot(
                slotKey = "passeggiata_cane|510",
                canonicalLabel = "passeggiata_cane",
                displayLabel = "Passeggiata cane",
                typicalTimeMinutes = 510,
                hitCount = 5,
                confidence = 0.75f,
                rawLabels = setOf("passeggiata cane"),
            ),
        )

        val tool = DeleteMemoryTool(unified, habitRepo)
        val result = tool.execute(
            ToolInvocation(name = "delete_memory", params = mapOf("query" to "passeggiata_cane")),
        )

        assertTrue(result is ToolResult.Success)
        assertTrue(habitRepo.listAll().isEmpty())
    }
}
