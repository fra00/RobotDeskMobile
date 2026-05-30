package com.example.mydeskrobot.memory.extract

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryExtractionDeltaTest {

    private val entries = listOf(
        ChatLogEntry(1, "user", "ciao"),
        ChatLogEntry(2, "assistant", "ciao"),
        ChatLogEntry(3, "user", "mi chiamo Francesco"),
    )

    @Test
    fun selectDelta_returnsUnprocessedTail() {
        val delta = MemoryExtractionDelta.selectDelta(entries, processedEntryCount = 2)
        assertEquals(1, delta.size)
        assertEquals("mi chiamo Francesco", delta[0].text)
    }

    @Test
    fun selectDelta_resetsWhenLogShrunk() {
        val delta = MemoryExtractionDelta.selectDelta(entries.take(2), processedEntryCount = 5)
        assertEquals(2, delta.size)
    }

    @Test
    fun extractJsonBody_stripsMarkdownFence() {
        val raw = """
            ```json
            {"facts":[{"category":"IDENTITY","value":"Francesco","confidence":0.9}]}
            ```
        """.trimIndent()
        val json = MemoryExtractionService.extractJsonBody(raw)
        assert(json.contains("\"facts\""))
        assert(!json.contains("```"))
    }
}
