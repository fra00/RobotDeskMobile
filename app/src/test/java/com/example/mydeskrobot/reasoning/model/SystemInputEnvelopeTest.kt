package com.example.mydeskrobot.reasoning.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemInputEnvelopeTest {

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
    fun `fromWellnessCheck always lists Today activities even when empty`() {
        val check = RobotInput.WellnessCheck(
            phase = com.example.mydeskrobot.domain.wellness.WellnessPhase.DOMAIN_SCORE,
            enabledDomainIds = setOf("pasti"),
            recentDailyActivities = emptyList(),
        )

        val envelope = SystemInputEnvelope.fromWellnessCheck(check)

        assertTrue(envelope.formattedForLlm.contains("Today activities:"))
        assertTrue(envelope.formattedForLlm.contains("(none logged today)"))
        assertTrue(envelope.formattedForLlm.contains("Recent social threads"))
        assertTrue(envelope.formattedForLlm.contains("Recent work threads"))
        assertTrue(envelope.formattedForLlm.contains("(none logged)"))
    }

    @Test
    fun `fromWellnessCheck lists logged Today activities`() {
        val check = RobotInput.WellnessCheck(
            phase = com.example.mydeskrobot.domain.wellness.WellnessPhase.DOMAIN_SCORE,
            enabledDomainIds = setOf("pasti"),
            recentDailyActivities = listOf("13:10 pranzo"),
            recentSocialThreads = listOf("12:00 Marco (WhatsApp)"),
            recentWorkThreads = listOf("11:00 Luca (Microsoft Teams)"),
        )

        val envelope = SystemInputEnvelope.fromWellnessCheck(check)

        assertTrue(envelope.formattedForLlm.contains("Today activities:"))
        assertTrue(envelope.formattedForLlm.contains("13:10 pranzo"))
        assertFalse(envelope.formattedForLlm.contains("(none logged today)"))
        assertTrue(envelope.formattedForLlm.contains("12:00 Marco (WhatsApp)"))
        assertTrue(envelope.formattedForLlm.contains("11:00 Luca (Microsoft Teams)"))
    }
}
