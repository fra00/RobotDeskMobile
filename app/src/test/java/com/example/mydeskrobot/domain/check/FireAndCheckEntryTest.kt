package com.example.mydeskrobot.domain.check

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun isSticky_trueWhenCommitmentAndNoDues() {
        val entry = sampleEntry(
            checkGoal = "Dichiarazione",
            verificationMessage = null,
            triggerReason = "assolutamente",
            kind = FireAndCheckKind.COMMITMENT,
        )
        assertTrue(entry.isSticky)
        assertTrue(
            isStickyCommitment(
                FireAndCheckKind.COMMITMENT,
                primaryDueAtMillis = null,
                verificationDueAtMillis = null,
            ),
        )
    }

    @Test
    fun isSticky_falseWhenTimedCommitment() {
        val entry = sampleEntry(
            checkGoal = "Documento",
            verificationMessage = "Verifica impegno: Documento",
            triggerReason = "assolutamente",
            kind = FireAndCheckKind.COMMITMENT,
            primaryDueAtMillis = 1_000L,
            verificationDueAtMillis = 2_000L,
        )
        assertFalse(entry.isSticky)
        assertFalse(
            isStickyCommitment(
                FireAndCheckKind.COMMITMENT,
                primaryDueAtMillis = 1_000L,
                verificationDueAtMillis = 2_000L,
            ),
        )
    }

    @Test
    fun isSticky_falseForPhysicalEvenWithNullDues() {
        assertFalse(
            isStickyCommitment(
                FireAndCheckKind.PHYSICAL,
                primaryDueAtMillis = null,
                verificationDueAtMillis = null,
            ),
        )
    }

    private fun sampleEntry(
        checkGoal: String?,
        verificationMessage: String?,
        triggerReason: String,
        kind: FireAndCheckKind = FireAndCheckKind.PHYSICAL,
        primaryDueAtMillis: Long? = null,
        verificationDueAtMillis: Long? = null,
    ) = FireAndCheckEntry(
        id = 1L,
        kind = kind,
        triggerReason = triggerReason,
        checkGoal = checkGoal,
        primaryMessage = "Sveglia",
        verificationMessage = verificationMessage,
        primaryDueAtMillis = primaryDueAtMillis,
        verificationDueAtMillis = verificationDueAtMillis,
        phase = FireAndCheckPhase.SCHEDULED,
    )
}
