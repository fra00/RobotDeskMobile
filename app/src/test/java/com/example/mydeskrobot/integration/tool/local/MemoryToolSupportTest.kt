package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.memory.db.MemoryCategory
import org.junit.Assert.assertEquals
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
}
