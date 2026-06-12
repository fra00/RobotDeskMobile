package com.example.mydeskrobot.reasoning.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryIntentDetectorTest {

    @Test
    fun detect_dogNameQuestion_isQuery() {
        val result = MemoryIntentDetector.detect("Come si chiama il mio cane?")
        assertEquals(MemoryRetrievalProfile.QUERY, result.primary)
    }

    @Test
    fun detect_takePhoto_isVision() {
        val result = MemoryIntentDetector.detect("Fai una foto")
        assertEquals(MemoryRetrievalProfile.VISION, result.primary)
    }

    @Test
    fun detect_todayAgenda_isPlan() {
        val result = MemoryIntentDetector.detect("Cosa devo fare oggi?")
        assertEquals(MemoryRetrievalProfile.PLAN, result.primary)
    }

    @Test
    fun detect_leisureSuggestion_isLeisure() {
        val result = MemoryIntentDetector.detect("Cosa posso guardare oggi?")
        assertEquals(MemoryRetrievalProfile.LEISURE, result.primary)
    }

    @Test
    fun detect_photoAndDogName_mergesVisionAndQuery() {
        val result = MemoryIntentDetector.detect("Fai una foto del mio cane, come si chiama?")
        assertTrue(result.includes(MemoryRetrievalProfile.VISION))
        assertTrue(result.includes(MemoryRetrievalProfile.QUERY))
    }

    @Test
    fun detect_genericPhrase_isDefault() {
        val result = MemoryIntentDetector.detect("Ciao come stai?")
        assertEquals(MemoryRetrievalProfile.DEFAULT, result.primary)
    }

    @Test
    fun detect_checkMemory_isQuery() {
        val result = MemoryIntentDetector.detect("Controlla la memoria sul cane")
        assertEquals(MemoryRetrievalProfile.QUERY, result.primary)
    }
}
