package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.domain.list.ListItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListToolSupportTest {

    @Test
    fun parseType_acceptsShopping() {
        assertEquals(ListItemType.SHOPPING, ListToolSupport.parseType("shopping"))
    }

    @Test
    fun parseType_acceptsTodo() {
        assertEquals(ListItemType.TODO, ListToolSupport.parseType("TODO"))
    }

    @Test
    fun parseType_blankReturnsNull() {
        assertNull(ListToolSupport.parseType(""))
        assertNull(ListToolSupport.parseType(null))
    }

    @Test
    fun parseType_invalidReturnsNull() {
        assertNull(ListToolSupport.parseType("invalid"))
    }

    @Test
    fun parseLimit_respectsBounds() {
        assertEquals(30, ListToolSupport.parseLimit(null))
        assertEquals(100, ListToolSupport.parseLimit(200))
        assertEquals(5, ListToolSupport.parseLimit(5))
    }

    @Test
    fun parseChecked_parsesBooleanAndStrings() {
        assertEquals(true, ListToolSupport.parseChecked(true))
        assertEquals(false, ListToolSupport.parseChecked(false))
        assertEquals(true, ListToolSupport.parseChecked("si"))
        assertEquals(true, ListToolSupport.parseChecked("sì"))
        assertEquals(false, ListToolSupport.parseChecked("no"))
        assertNull(ListToolSupport.parseChecked(null))
        assertNull(ListToolSupport.parseChecked("maybe"))
    }

    @Test
    fun entityToMap_lowercasesType() {
        val entity = com.example.mydeskrobot.data.lists.db.ListItemEntity(
            id = 1L,
            type = ListItemType.SHOPPING,
            text = "latte",
            checked = false,
            createdAtMillis = 100L,
            updatedAtMillis = 200L,
        )
        val map = ListToolSupport.entityToMap(entity)
        assertEquals("shopping", map["type"])
        assertEquals("latte", map["text"])
        assertEquals(1L, map["id"])
    }
}
