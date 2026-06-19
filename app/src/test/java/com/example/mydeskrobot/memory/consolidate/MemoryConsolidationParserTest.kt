package com.example.mydeskrobot.memory.consolidate

import com.example.mydeskrobot.memory.db.MemoryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryConsolidationParserTest {

    @Test
    fun parseLines_parsesCategoryPrefixedRows() {
        val raw = """
            (ROUTINE) L'utente lavora lun-gio 9:00-13:00 e 14:00-18:00; ven 9:00-13:00.
            (FACT) Il cane si chiama Brina.
        """.trimIndent()

        val parsed = MemoryConsolidationParser.parseLines(raw)

        assertEquals(2, parsed.size)
        assertEquals(MemoryCategory.ROUTINE, parsed[0].category)
        assertTrue(parsed[0].value.contains("lun-gio"))
        assertEquals(MemoryCategory.FACT, parsed[1].category)
    }

    @Test
    fun parseLines_ignoresPreambleAndBlankLines() {
        val raw = """
            Ecco le memorie consolidate:
            
            1. (IDENTITY) L'utente si chiama Francesco
        """.trimIndent()

        val parsed = MemoryConsolidationParser.parseLines(raw)

        assertEquals(0, parsed.size)
    }

    @Test
    fun parseLines_stripsCodeFence() {
        val raw = """
            ```text
            (PREFERENCE) L'utente ama il MotoGP.
            ```
        """.trimIndent()

        val parsed = MemoryConsolidationParser.parseLines(raw)

        assertEquals(1, parsed.size)
        assertEquals(MemoryCategory.PREFERENCE, parsed[0].category)
    }

    @Test
    fun parseLines_parsesQuotedArrayLikeOutput() {
        val raw = """
            [
              "(IDENTITY) L'assistente si chiama Robottino.",
              "(PREFERENCE) L'utente ama il MotoGP."
            ]
        """.trimIndent()

        val parsed = MemoryConsolidationParser.parseLines(raw)

        assertEquals(2, parsed.size)
        assertEquals(MemoryCategory.IDENTITY, parsed[0].category)
        assertEquals(MemoryCategory.PREFERENCE, parsed[1].category)
    }
}
