package com.example.mydeskrobot.domain.awareness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAwarenessStateTest {

    @Test
    fun `initial state has unknown mood`() {
        val state = UserAwarenessState.forToday()
        assertEquals(UserMood.UNKNOWN, state.inferredMood)
    }

    @Test
    fun `userKnowsAbout returns false for empty set`() {
        val state = UserAwarenessState.forToday()
        assertFalse(state.userKnowsAbout("meteo"))
    }

    @Test
    fun `withUserKnowsAbout adds topic`() {
        val state = UserAwarenessState.forToday()
            .withUserKnowsAbout("meteo")
        assertTrue(state.userKnowsAbout("meteo"))
        assertTrue(state.userKnowsAbout("METEO"))
    }

    @Test
    fun `withUserKnowsAbout removes from doesNotKnow set`() {
        val state = UserAwarenessState.forToday()
            .withNewInfoAvailable("email")
            .withUserKnowsAbout("email")
        assertTrue(state.userKnowsAbout("email"))
        assertFalse(state.userProbablyDoesNotKnow.contains("email"))
    }

    @Test
    fun `withNewInfoAvailable adds to doesNotKnow set`() {
        val state = UserAwarenessState.forToday()
            .withNewInfoAvailable("nuova_email")
        assertTrue(state.userProbablyDoesNotKnow.contains("nuova_email"))
    }

    @Test
    fun `withUserResponse increments short response count`() {
        val state = UserAwarenessState.forToday()
            .withUserResponse(3)
        assertEquals(1, state.shortResponsesCount)
        assertEquals(0, state.engagedResponsesCount)
    }

    @Test
    fun `withUserResponse increments engaged response count`() {
        val state = UserAwarenessState.forToday()
            .withUserResponse(25)
        assertEquals(0, state.shortResponsesCount)
        assertEquals(1, state.engagedResponsesCount)
    }

    @Test
    fun `withUserResponse tracks recent response lengths`() {
        val state = UserAwarenessState.forToday()
            .withUserResponse(5)
            .withUserResponse(10)
            .withUserResponse(15)
        assertEquals(3, state.recentResponseLengths.size)
        assertEquals(listOf(5, 10, 15), state.recentResponseLengths)
    }

    @Test
    fun `withBusyMention sets mood to busy`() {
        val state = UserAwarenessState.forToday()
            .withBusyMention(1000L)
        assertEquals(UserMood.BUSY, state.inferredMood)
        assertEquals(1000L, state.lastBusyMentionMillis)
    }

    @Test
    fun `withPositiveInteraction sets mood to relaxed`() {
        val state = UserAwarenessState.forToday()
            .withPositiveInteraction(2000L)
        assertEquals(UserMood.RELAXED, state.inferredMood)
        assertEquals(2000L, state.lastPositiveInteractionMillis)
    }

    @Test
    fun `multiple short responses infer busy mood`() {
        var state = UserAwarenessState.forToday()
        repeat(5) { state = state.withUserResponse(3) }
        assertEquals(UserMood.BUSY, state.inferredMood)
    }

    @Test
    fun `multiple engaged responses infer relaxed mood`() {
        var state = UserAwarenessState.forToday()
        repeat(5) { state = state.withUserResponse(25) }
        assertEquals(UserMood.RELAXED, state.inferredMood)
    }

    @Test
    fun `todayKey generates consistent key for same day`() {
        val key1 = UserAwarenessState.todayKey()
        val key2 = UserAwarenessState.todayKey()
        assertEquals(key1, key2)
    }

    @Test
    fun `forToday creates state with today's key`() {
        val state = UserAwarenessState.forToday()
        assertEquals(UserAwarenessState.todayKey(), state.dateKey)
    }
}
