package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.data.predictivity.HabitSlotCodec
import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryToolSupportTest {

    @Test
    fun parseCategory_acceptsIdentity() {
        assertEquals(MemoryCategory.IDENTITY, MemoryToolSupport.parseCategory("identity"))
    }

    @Test
    fun parseCategory_blankReturnsNull() {
        assertNull(MemoryToolSupport.parseCategory(""))
        assertNull(MemoryToolSupport.parseCategory(null))
    }

    @Test
    fun parseConfidence_clampsAndDefaults() {
        assertEquals(0.85f, MemoryToolSupport.parseConfidence(null))
        assertEquals(1f, MemoryToolSupport.parseConfidence(1.5))
        assertEquals(0.5f, MemoryToolSupport.parseConfidence("0.5"))
    }

    @Test
    fun parseLimit_respectsBounds() {
        assertEquals(20, MemoryToolSupport.parseLimit(null))
        assertEquals(50, MemoryToolSupport.parseLimit(100))
        assertEquals(3, MemoryToolSupport.parseLimit(3))
    }

    @Test
    fun documentToMap_labelsHabitSlotAsExpectedHabit() {
        val slot = HabitSlot(
            slotKey = "tapis_roulant|1140",
            canonicalLabel = "tapis_roulant",
            displayLabel = "tapis roulant",
            typicalTimeMinutes = 1140,
            hitCount = 4,
            confidence = 0.75f,
        )
        val entity = MemoryDocumentEntity(
            id = 9L,
            value = HabitSlotCodec.encode(slot),
            kind = MemoryDocumentKind.AUTONOMY.name,
            category = MemoryCategory.PATTERN.name,
            source = MemoryDocumentSource.EXTRACTOR.name,
            confidence = 0.75f,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val map = MemoryToolSupport.documentToMap(entity)
        assertEquals(MemoryToolSupport.MEMORY_ROLE_EXPECTED_HABIT, map["memory_role"])
        assertEquals("tapis roulant (di solito intorno alle 19:00)", map["value"])
        assertFalse(map["value"].toString().contains("HABIT_SLOT:v1:"))
        assertEquals(MemoryCategory.PATTERN.name, map["category"])
    }

    @Test
    fun documentToMap_userFactRole() {
        val entity = MemoryDocumentEntity(
            id = 1L,
            value = "L'utente si chiama Francesco",
            kind = MemoryDocumentKind.USER_FACT.name,
            category = MemoryCategory.IDENTITY.name,
            source = MemoryDocumentSource.TOOL.name,
            confidence = 0.9f,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val map = MemoryToolSupport.documentToMap(entity)
        assertEquals(MemoryToolSupport.MEMORY_ROLE_USER_FACT, map["memory_role"])
        assertEquals("L'utente si chiama Francesco", map["value"])
    }
}
