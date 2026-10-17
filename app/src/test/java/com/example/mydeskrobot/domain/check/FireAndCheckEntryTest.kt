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

    @Test
    fun isCommitment_trueForCommitmentKind() {
        val entry = sampleEntry(
            checkGoal = "Inviare documento",
            verificationMessage = null,
            triggerReason = "assolutamente",
            kind = FireAndCheckKind.COMMITMENT,
        )
        assertEquals(true, entry.isCommitment)
    }

    private fun sampleEntry(
        checkGoal: String?,
        verificationMessage: String?,
        triggerReason: String,
        kind: FireAndCheckKind = FireAndCheckKind.PHYSICAL,
    ) = FireAndCheckEntry(
        id = 1L,
        kind = kind,
        triggerReason = triggerReason,
        checkGoal = checkGoal,
        primaryMessage = "Sveglia",
        verificationMessage = verificationMessage,
        primaryDueAtMillis = null,
        verificationDueAtMillis = null,
        phase = FireAndCheckPhase.SCHEDULED,
    )
}
