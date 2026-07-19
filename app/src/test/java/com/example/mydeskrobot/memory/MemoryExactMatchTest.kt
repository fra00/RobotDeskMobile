package com.example.mydeskrobot.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExactMatchTest {

    @Test
    fun isExactDuplicate_matches_normalized_whitespace_and_case() {
        assertTrue(
            MemoryExactMatch.isExactDuplicate(
                category = com.example.mydeskrobot.memory.db.MemoryCategory.FACT,
                valueA = "  L'utente ama  il cinema ",
                valueB = "l'utente ama il cinema",
            ),
        )
    }

    @Test
    fun isExactDuplicate_rejects_paraphrase() {
        assertFalse(
            MemoryExactMatch.isExactDuplicate(
                category = com.example.mydeskrobot.memory.db.MemoryCategory.IDENTITY,
                valueA = "L'utente si chiama Francesco",
                valueB = "The user's name is Francesco",
            ),
        )
    }

    @Test
    fun isExactDuplicate_requires_same_category() {
        assertFalse(
            MemoryExactMatch.isExactDuplicate(
                category = com.example.mydeskrobot.memory.db.MemoryCategory.IDENTITY,
                valueA = "Francesco",
                valueB = "Francesco",
                categoryB = com.example.mydeskrobot.memory.db.MemoryCategory.FACT,
            ),
        )
    }
}
