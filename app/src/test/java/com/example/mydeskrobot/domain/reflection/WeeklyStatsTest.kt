package com.example.mydeskrobot.domain.reflection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyStatsTest {

    @Test
    fun `initial state has zero counts`() {
        val stats = WeeklyStats.forCurrentWeek()

        assertEquals(0, stats.totalInteractions)
        assertEquals(0, stats.totalProactiveSpeaks)
        assertEquals(0, stats.positiveResponses)
        assertEquals(0, stats.ignoredSuggestions)
        assertTrue(stats.usefulTopics.isEmpty())
        assertTrue(stats.ignoredTopics.isEmpty())
    }

    @Test
    fun `withInteraction increments count`() {
        val stats = WeeklyStats.forCurrentWeek()
            .withInteraction()
            .withInteraction()

        assertEquals(2, stats.totalInteractions)
    }

    @Test
    fun `withProactiveSpeak increments count`() {
        val stats = WeeklyStats.forCurrentWeek()
            .withProactiveSpeak()
            .withProactiveSpeak()
            .withProactiveSpeak()

        assertEquals(3, stats.totalProactiveSpeaks)
    }

    @Test
    fun `withPositiveResponse increments count and tracks topic`() {
        val stats = WeeklyStats.forCurrentWeek()
            .withPositiveResponse("meteo")
            .withPositiveResponse("meteo")
            .withPositiveResponse("promemoria")

        assertEquals(3, stats.positiveResponses)
        assertEquals(2, stats.usefulTopics["meteo"])
        assertEquals(1, stats.usefulTopics["promemoria"])
    }

    @Test
    fun `withIgnoredSuggestion increments count and tracks topic`() {
        val stats = WeeklyStats.forCurrentWeek()
            .withIgnoredSuggestion("serie TV")
            .withIgnoredSuggestion("serie TV")

        assertEquals(2, stats.ignoredSuggestions)
        assertEquals(2, stats.ignoredTopics["serie TV"])
    }

    @Test
    fun `successRate is zero when no proactive speaks`() {
        val stats = WeeklyStats.forCurrentWeek()

        assertEquals(0f, stats.successRate())
    }

    @Test
    fun `successRate calculates correctly`() {
        val stats = WeeklyStats.forCurrentWeek()
            .withPositiveResponse(null)
            .withPositiveResponse(null)
            .withIgnoredSuggestion(null)

        assertEquals(0.666f, stats.successRate(), 0.01f)
    }

    @Test
    fun `topUsefulTopics returns sorted by count`() {
        val stats = WeeklyStats.forCurrentWeek()
            .withPositiveResponse("meteo")
            .withPositiveResponse("promemoria")
            .withPositiveResponse("promemoria")
            .withPositiveResponse("promemoria")
            .withPositiveResponse("meteo")

        val top = stats.topUsefulTopics(2)

        assertEquals(listOf("promemoria", "meteo"), top)
    }

    @Test
    fun `topIgnoredTopics returns sorted by count`() {
        val stats = WeeklyStats.forCurrentWeek()
            .withIgnoredSuggestion("serie TV")
            .withIgnoredSuggestion("serie TV")
            .withIgnoredSuggestion("news")

        val top = stats.topIgnoredTopics(2)

        assertEquals(listOf("serie TV", "news"), top)
    }

    @Test
    fun `withReflectionDone sets timestamp`() {
        val stats = WeeklyStats.forCurrentWeek()
        val timestamp = 1_000_000L

        val updated = stats.withReflectionDone(timestamp)

        assertEquals(timestamp, updated.lastReflectionMillis)
    }

    @Test
    fun `currentWeekKey format is YYYYWW`() {
        val key = WeeklyStats.currentWeekKey()

        assertTrue(key > 202000)
        assertTrue(key < 210000)
    }

    @Test
    fun `forCurrentWeek sets weekKey`() {
        val stats = WeeklyStats.forCurrentWeek()
        val expectedKey = WeeklyStats.currentWeekKey()

        assertEquals(expectedKey, stats.weekKey)
    }

    @Test
    fun `multiple operations chain correctly`() {
        val stats = WeeklyStats.forCurrentWeek()
            .withInteraction()
            .withInteraction()
            .withProactiveSpeak()
            .withPositiveResponse("meteo")
            .withIgnoredSuggestion("serie TV")
            .withReflectionDone(12345L)

        assertEquals(2, stats.totalInteractions)
        assertEquals(1, stats.totalProactiveSpeaks)
        assertEquals(1, stats.positiveResponses)
        assertEquals(1, stats.ignoredSuggestions)
        assertEquals(12345L, stats.lastReflectionMillis)
    }

    @Test
    fun `withPositiveResponse without topic increments count only`() {
        val stats = WeeklyStats.forCurrentWeek()
            .withPositiveResponse(null)

        assertEquals(1, stats.positiveResponses)
        assertTrue(stats.usefulTopics.isEmpty())
    }
}
