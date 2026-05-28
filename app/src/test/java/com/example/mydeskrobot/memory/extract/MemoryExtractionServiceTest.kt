package com.example.mydeskrobot.memory.extract

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryExtractionServiceTest {

    @Test
    fun parseConversationLogExtractsUserAndRobotLines() {
        val log = """
            Tu: ciao sono Luca

            Robot: piacere Luca

            Tu: mi piace il cinema
        """.trimIndent()

        val entries = MemoryExtractionService.extractEntriesFromConversationLog(log)
        assertEquals(3, entries.size)
        assertEquals("user", entries[0].role)
        assertEquals("assistant", entries[1].role)
        assertEquals("mi piace il cinema", entries[2].text)
    }
}
