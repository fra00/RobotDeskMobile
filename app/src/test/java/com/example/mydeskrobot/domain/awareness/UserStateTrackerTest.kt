package com.example.mydeskrobot.domain.awareness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserStateTrackerTest {

    @Test
    fun `analyzeUserText tracks word count`() {
        val state = UserAwarenessState.forToday()
        val result = UserStateTracker.analyzeUserText("Ciao come stai oggi amico", state)
        assertEquals(1, result.recentResponseLengths.size)
        assertEquals(5, result.recentResponseLengths[0])
    }

    @Test
    fun `analyzeUserText detects busy keywords`() {
        val state = UserAwarenessState.forToday()
        val result = UserStateTracker.analyzeUserText("Sono occupato adesso", state)
        assertEquals(UserMood.BUSY, result.inferredMood)
        assertNotNull(result.lastBusyMentionMillis)
    }

    @Test
    fun `analyzeUserText detects meeting keyword`() {
        val state = UserAwarenessState.forToday()
        val result = UserStateTracker.analyzeUserText("Devo andare alla riunione", state)
        assertEquals(UserMood.BUSY, result.inferredMood)
    }

    @Test
    fun `analyzeUserText detects positive keywords`() {
        val state = UserAwarenessState.forToday()
        val result = UserStateTracker.analyzeUserText("Grazie mille, sei fantastico!", state)
        assertEquals(UserMood.RELAXED, result.inferredMood)
        assertNotNull(result.lastPositiveInteractionMillis)
    }

    @Test
    fun `analyzeUserText extracts meteo topic`() {
        val state = UserAwarenessState.forToday()
        val result = UserStateTracker.analyzeUserText("Che tempo fa oggi?", state)
        assertTrue(result.userKnowsAbout("meteo"))
    }

    @Test
    fun `analyzeUserText extracts reminder topic`() {
        val state = UserAwarenessState.forToday()
        val result = UserStateTracker.analyzeUserText("Ricordami di chiamare Marco", state)
        assertTrue(result.userKnowsAbout("reminder"))
    }

    @Test
    fun `analyzeUserText extracts musica topic`() {
        val state = UserAwarenessState.forToday()
        val result = UserStateTracker.analyzeUserText("Metti della musica", state)
        assertTrue(result.userKnowsAbout("musica"))
    }

    @Test
    fun `analyzeRobotResponse marks topics as known`() {
        val state = UserAwarenessState.forToday()
        val result = UserStateTracker.analyzeRobotResponse(
            "Oggi il meteo sarà soleggiato con temperature miti",
            state
        )
        assertTrue(result.userKnowsAbout("meteo"))
    }

    @Test
    fun `seemsBusy returns true for busy mood`() {
        val state = UserAwarenessState.forToday().copy(inferredMood = UserMood.BUSY)
        assertTrue(UserStateTracker.seemsBusy(state))
    }

    @Test
    fun `seemsBusy returns false for relaxed mood`() {
        val state = UserAwarenessState.forToday().copy(inferredMood = UserMood.RELAXED)
        assertFalse(UserStateTracker.seemsBusy(state))
    }

    @Test
    fun `interventionConfidenceModifier returns low for busy`() {
        val state = UserAwarenessState.forToday().copy(inferredMood = UserMood.BUSY)
        assertEquals(0.5f, UserStateTracker.interventionConfidenceModifier(state))
    }

    @Test
    fun `interventionConfidenceModifier returns high for relaxed`() {
        val state = UserAwarenessState.forToday().copy(inferredMood = UserMood.RELAXED)
        assertEquals(1.2f, UserStateTracker.interventionConfidenceModifier(state))
    }

    @Test
    fun `interventionConfidenceModifier returns neutral for unknown`() {
        val state = UserAwarenessState.forToday()
        assertEquals(0.8f, UserStateTracker.interventionConfidenceModifier(state))
    }

    @Test
    fun `shouldMentionTopic returns false for known topic`() {
        val state = UserAwarenessState.forToday().withUserKnowsAbout("meteo")
        assertFalse(UserStateTracker.shouldMentionTopic("meteo", state))
    }

    @Test
    fun `shouldMentionTopic returns true for unknown topic`() {
        val state = UserAwarenessState.forToday()
        assertTrue(UserStateTracker.shouldMentionTopic("meteo", state))
    }
}
