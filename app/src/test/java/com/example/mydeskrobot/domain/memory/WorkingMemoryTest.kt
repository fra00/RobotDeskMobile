package com.example.mydeskrobot.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkingMemoryTest {

    @Test
    fun `initial state has zero interactions`() {
        val memory = WorkingMemory.forToday()

        assertEquals(0, memory.todayInteractions)
        assertEquals(0, memory.proactiveSpeaksToday)
        assertEquals(0, memory.ignoredSuggestionsToday)
        assertTrue(memory.topicsDiscussedToday.isEmpty())
        assertNull(memory.lastProactiveSpeakMillis)
    }

    @Test
    fun `withInteraction increments count`() {
        val memory = WorkingMemory.forToday()

        val updated = memory.withInteraction().withInteraction()

        assertEquals(2, updated.todayInteractions)
    }

    @Test
    fun `withTopic adds topic`() {
        val memory = WorkingMemory.forToday()

        val updated = memory.withTopic("meteo")

        assertTrue(updated.hasDiscussedTopic("meteo"))
        assertEquals(listOf("meteo"), updated.topicsDiscussedToday)
    }

    @Test
    fun `withTopic is case insensitive for check`() {
        val memory = WorkingMemory.forToday().withTopic("Meteo")

        assertTrue(memory.hasDiscussedTopic("meteo"))
        assertTrue(memory.hasDiscussedTopic("METEO"))
        assertTrue(memory.hasDiscussedTopic("Meteo"))
    }

    @Test
    fun `withTopic does not add duplicate`() {
        val memory = WorkingMemory.forToday()
            .withTopic("meteo")
            .withTopic("METEO")
            .withTopic("Meteo")

        assertEquals(1, memory.topicsDiscussedToday.size)
    }

    @Test
    fun `withProactiveSpeak increments count and sets timestamp`() {
        val memory = WorkingMemory.forToday()
        val timestamp = 1_000_000L

        val updated = memory.withProactiveSpeak(timestamp)

        assertEquals(1, updated.proactiveSpeaksToday)
        assertEquals(timestamp, updated.lastProactiveSpeakMillis)
    }

    @Test
    fun `withIgnoredSuggestion increments count`() {
        val memory = WorkingMemory.forToday()

        val updated = memory.withIgnoredSuggestion().withIgnoredSuggestion()

        assertEquals(2, updated.ignoredSuggestionsToday)
    }

    @Test
    fun `minutesSinceLastProactiveSpeak returns null if never spoke`() {
        val memory = WorkingMemory.forToday()

        assertNull(memory.minutesSinceLastProactiveSpeak())
    }

    @Test
    fun `minutesSinceLastProactiveSpeak calculates correctly`() {
        val baseTime = 1_000_000_000L
        val memory = WorkingMemory.forToday().withProactiveSpeak(baseTime)

        val minutes = memory.minutesSinceLastProactiveSpeak(baseTime + 30 * 60_000)

        assertEquals(30L, minutes)
    }

    @Test
    fun `hasDiscussedTopic returns false for unknown topic`() {
        val memory = WorkingMemory.forToday().withTopic("meteo")

        assertFalse(memory.hasDiscussedTopic("news"))
    }

    @Test
    fun `todayKey format is YYYYMMDD`() {
        val key = WorkingMemory.todayKey()

        assertTrue(key > 20000000)
        assertTrue(key < 30000000)
    }

    @Test
    fun `forToday sets dateKey`() {
        val memory = WorkingMemory.forToday()
        val expectedKey = WorkingMemory.todayKey()

        assertEquals(expectedKey, memory.dateKey)
    }

    @Test
    fun `multiple operations chain correctly`() {
        val memory = WorkingMemory.forToday()
            .withInteraction()
            .withInteraction()
            .withTopic("meteo")
            .withTopic("notizie")
            .withProactiveSpeak(1000L)
            .withIgnoredSuggestion()

        assertEquals(2, memory.todayInteractions)
        assertEquals(2, memory.topicsDiscussedToday.size)
        assertEquals(1, memory.proactiveSpeaksToday)
        assertEquals(1, memory.ignoredSuggestionsToday)
        assertEquals(1000L, memory.lastProactiveSpeakMillis)
    }
}
