package com.example.mydeskrobot.reasoning.model

import org.junit.Assert.*
import org.junit.Test

class SystemInputEnvelopeTest {

    @Test
    fun `fromHeartbeat formats time correctly`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 15,
            currentHour = 14,
            currentMinute = 5,
            dayOfWeek = "mercoledì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertTrue(envelope.formattedForLlm.contains("Ora: 14:05"))
    }

    @Test
    fun `fromHeartbeat includes pending reminders if present`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 10,
            currentHour = 10,
            currentMinute = 0,
            dayOfWeek = "giovedì",
            pendingRemindersCount = 3,
            relevantRoutines = emptyList(),
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertTrue(envelope.formattedForLlm.contains("Promemoria attivi: 3"))
    }

    @Test
    fun `fromHeartbeat omits reminders line if zero`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 10,
            currentHour = 10,
            currentMinute = 0,
            dayOfWeek = "venerdì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertFalse(envelope.formattedForLlm.contains("Promemoria attivi"))
    }

    @Test
    fun `fromHeartbeat includes routines`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 5,
            currentHour = 8,
            currentMinute = 30,
            dayOfWeek = "sabato",
            pendingRemindersCount = 0,
            relevantRoutines = listOf("Colazione", "Esercizio mattutino"),
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertTrue(envelope.formattedForLlm.contains("Routine: Colazione"))
        assertTrue(envelope.formattedForLlm.contains("Routine: Esercizio mattutino"))
    }

    @Test
    fun `fromHeartbeat dedupKey uses minute bucket`() {
        val heartbeat1 = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 5,
            currentHour = 10,
            currentMinute = 0,
            dayOfWeek = "domenica",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            timestamp = 120000L,
        )
        val heartbeat2 = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 5,
            currentHour = 10,
            currentMinute = 0,
            dayOfWeek = "domenica",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            timestamp = 125000L,
        )

        val envelope1 = SystemInputEnvelope.fromHeartbeat(heartbeat1)
        val envelope2 = SystemInputEnvelope.fromHeartbeat(heartbeat2)

        assertEquals(envelope1.dedupKey, envelope2.dedupKey)
    }

    @Test
    fun `from dispatches to fromHeartbeat for Heartbeat input`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 10,
            currentHour = 12,
            currentMinute = 0,
            dayOfWeek = "lunedì",
            pendingRemindersCount = 1,
            relevantRoutines = emptyList(),
        )

        val envelope = SystemInputEnvelope.from(heartbeat)

        assertTrue(envelope.formattedForLlm.contains("[SYSTEM_INPUT: heartbeat]"))
    }

    @Test
    fun `fromHeartbeat includes mood when present`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 45,
            currentHour = 15,
            currentMinute = 30,
            dayOfWeek = "martedì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            moodLabel = "bored",
            moodIntensity = 0.3f,
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertTrue(envelope.formattedForLlm.contains("Stato emotivo: bored (30%)"))
    }

    @Test
    fun `fromHeartbeat omits mood when null`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 10,
            currentHour = 10,
            currentMinute = 0,
            dayOfWeek = "mercoledì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            moodLabel = null,
            moodIntensity = null,
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertFalse(envelope.formattedForLlm.contains("Stato emotivo"))
    }

    @Test
    fun `fromHeartbeat includes working memory when present`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 30,
            currentHour = 14,
            currentMinute = 0,
            dayOfWeek = "giovedì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            todayInteractions = 5,
            proactiveSpeaksToday = 2,
            minutesSinceLastProactiveSpeak = 15,
            topicsDiscussedToday = listOf("meteo", "notizie"),
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertTrue(envelope.formattedForLlm.contains("Interazioni oggi: 5"))
        assertTrue(envelope.formattedForLlm.contains("Interventi proattivi oggi: 2"))
        assertTrue(envelope.formattedForLlm.contains("Minuti dall'ultimo intervento: 15"))
        assertTrue(envelope.formattedForLlm.contains("Topic già discussi oggi: meteo, notizie"))
    }

    @Test
    fun `fromHeartbeat omits working memory fields when zero`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 10,
            currentHour = 10,
            currentMinute = 0,
            dayOfWeek = "venerdì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            todayInteractions = 0,
            proactiveSpeaksToday = 0,
            minutesSinceLastProactiveSpeak = null,
            topicsDiscussedToday = emptyList(),
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertFalse(envelope.formattedForLlm.contains("Interazioni oggi"))
        assertFalse(envelope.formattedForLlm.contains("Interventi proattivi oggi"))
        assertFalse(envelope.formattedForLlm.contains("Minuti dall'ultimo intervento"))
        assertFalse(envelope.formattedForLlm.contains("Topic già discussi oggi"))
    }

    @Test
    fun `fromWeeklyReflection formats statistics correctly`() {
        val reflection = RobotInput.WeeklyReflection(
            totalInteractions = 42,
            totalProactiveSpeaks = 12,
            positiveResponses = 8,
            ignoredSuggestions = 4,
            usefulTopics = listOf("meteo", "promemoria"),
            ignoredTopics = listOf("serie TV"),
            successRatePercent = 66,
        )

        val envelope = SystemInputEnvelope.fromWeeklyReflection(reflection)

        assertTrue(envelope.formattedForLlm.contains("[SYSTEM_INPUT: weekly_reflection]"))
        assertTrue(envelope.formattedForLlm.contains("Interazioni utente: 42"))
        assertTrue(envelope.formattedForLlm.contains("Interventi proattivi: 12"))
        assertTrue(envelope.formattedForLlm.contains("Risposte positive: 8"))
        assertTrue(envelope.formattedForLlm.contains("Ignorati/rifiutati: 4"))
        assertTrue(envelope.formattedForLlm.contains("Tasso di successo: 66%"))
        assertTrue(envelope.formattedForLlm.contains("Topic utili: meteo, promemoria"))
        assertTrue(envelope.formattedForLlm.contains("Topic ignorati: serie TV"))
    }

    @Test
    fun `fromWeeklyReflection omits empty topic lists`() {
        val reflection = RobotInput.WeeklyReflection(
            totalInteractions = 10,
            totalProactiveSpeaks = 5,
            positiveResponses = 3,
            ignoredSuggestions = 2,
            usefulTopics = emptyList(),
            ignoredTopics = emptyList(),
            successRatePercent = 60,
        )

        val envelope = SystemInputEnvelope.fromWeeklyReflection(reflection)

        assertFalse(envelope.formattedForLlm.contains("Topic utili"))
        assertFalse(envelope.formattedForLlm.contains("Topic ignorati"))
    }

    @Test
    fun `from dispatches to fromWeeklyReflection for reflection input`() {
        val reflection = RobotInput.WeeklyReflection(
            totalInteractions = 5,
            totalProactiveSpeaks = 2,
            positiveResponses = 1,
            ignoredSuggestions = 1,
            usefulTopics = emptyList(),
            ignoredTopics = emptyList(),
            successRatePercent = 50,
        )

        val envelope = SystemInputEnvelope.from(reflection)

        assertTrue(envelope.formattedForLlm.contains("[SYSTEM_INPUT: weekly_reflection]"))
    }

    @Test
    fun `fromHeartbeat includes user mood when present`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 10,
            currentHour = 14,
            currentMinute = 30,
            dayOfWeek = "lunedì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            userMood = "busy",
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertTrue(envelope.formattedForLlm.contains("Umore utente percepito: busy"))
    }

    @Test
    fun `fromHeartbeat omits user mood when unknown`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 10,
            currentHour = 14,
            currentMinute = 30,
            dayOfWeek = "lunedì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            userMood = "unknown",
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertFalse(envelope.formattedForLlm.contains("Umore utente percepito"))
    }

    @Test
    fun `fromHeartbeat includes user probably knows list`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 10,
            currentHour = 14,
            currentMinute = 30,
            dayOfWeek = "lunedì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            userProbablyKnows = listOf("meteo", "notizie"),
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertTrue(envelope.formattedForLlm.contains("L'utente probabilmente sa già: meteo, notizie"))
    }

    @Test
    fun `fromHeartbeat omits user probably knows when empty`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 10,
            currentHour = 14,
            currentMinute = 30,
            dayOfWeek = "lunedì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            userProbablyKnows = emptyList(),
        )

        val envelope = SystemInputEnvelope.fromHeartbeat(heartbeat)

        assertFalse(envelope.formattedForLlm.contains("L'utente probabilmente sa già"))
    }
}
