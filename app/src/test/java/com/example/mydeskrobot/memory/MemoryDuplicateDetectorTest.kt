package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.db.MemoryCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryDuplicateDetectorTest {

    @Test
    fun detectsSameFactDifferentWordingItalian() {
        assertTrue(
            MemoryDuplicateDetector.areDuplicates(
                "L'utente ha un cane di nome Brina",
                "Il cane dell'utente si chiama Brina",
                MemoryCategory.FACT,
            ),
        )
    }

    @Test
    fun detectsItalianEnglishNamePair() {
        assertTrue(
            MemoryDuplicateDetector.areDuplicates(
                "L'utente si chiama Francesco",
                "The user's name is Francesco",
                MemoryCategory.IDENTITY,
            ),
        )
    }

    @Test
    fun rejectsUnrelatedFacts() {
        assertFalse(
            MemoryDuplicateDetector.areDuplicates(
                "L'utente ama il cinema",
                "L'utente ha un cane di nome Brina",
                MemoryCategory.PREFERENCE,
            ),
        )
    }
}
