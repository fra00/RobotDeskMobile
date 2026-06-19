package com.example.mydeskrobot.memory.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationLogParserTest {

    @Test
    fun `parses user and robot lines`() {
        val log = """
            Tu: ciao

            Robot: Ciao!

            Tu: domani cinema
        """.trimIndent()
        val entries = ConversationLogParser.parseAllEntries(log)
        assertEquals(3, entries.size)
        assertEquals("user", entries[0].role)
        assertEquals("ciao", entries[0].text)
        assertEquals("assistant", entries[1].role)
        assertEquals("user", entries[2].role)
    }

    @Test
    fun `parses system notification line`() {
        val log = "Sistema (WhatsApp): domani andiamo al cinema"
        val entries = ConversationLogParser.parseAllEntries(log)
        assertEquals(1, entries.size)
        assertEquals("system", entries[0].role)
        assertEquals("WhatsApp: domani andiamo al cinema", entries[0].text)
    }

    @Test
    fun `parseUserAssistantEntries excludes system`() {
        val log = """
            Tu: ok
            Sistema (WhatsApp): promo -50%
            Robot: va bene
        """.trimIndent()
        val entries = ConversationLogParser.parseUserAssistantEntries(log)
        assertEquals(2, entries.size)
        assertTrue(entries.none { it.role == "system" })
    }
}
