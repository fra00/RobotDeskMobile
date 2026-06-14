package com.example.mydeskrobot.domain.check

import org.junit.Assert.assertEquals
import org.junit.Test

class FireAndCheckEntryTest {

    @Test
    fun detailText_prefersCheckGoal() {
        val entry = sampleEntry(
            checkGoal = "Verificare se l'utente è ancora alla scrivania",
            verificationMessage = "Verifica pranzo",
            triggerReason = "Ricordami di pranzare",
        )
        assertEquals("Verificare se l'utente è ancora alla scrivania", entry.detailText())
    }

    @Test
    fun detailText_fallsBackToTriggerReason() {
        val entry = sampleEntry(
            checkGoal = null,
            verificationMessage = null,
            triggerReason = "Svegliami alle 21",
        )
        assertEquals("Svegliami alle 21", entry.detailText())
    }

    private fun sampleEntry(
        checkGoal: String?,
        verificationMessage: String?,
        triggerReason: String,
    ) = FireAndCheckEntry(
        id = 1L,
        triggerReason = triggerReason,
        checkGoal = checkGoal,
        primaryMessage = "Sveglia",
        verificationMessage = verificationMessage,
        primaryDueAtMillis = null,
        verificationDueAtMillis = null,
        phase = FireAndCheckPhase.SCHEDULED,
    )
}
