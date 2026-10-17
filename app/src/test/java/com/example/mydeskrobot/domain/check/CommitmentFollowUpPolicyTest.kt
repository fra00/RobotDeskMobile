package com.example.mydeskrobot.domain.check

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentFollowUpPolicyTest {

    @Test
    fun `isCommitmentVerificationMessage detects prefix`() {
        assertTrue(
            CommitmentFollowUpPolicy.isCommitmentVerificationMessage(
                "Verifica impegno: Inviare il documento",
            ),
        )
        assertTrue(
            CommitmentFollowUpPolicy.isCommitmentVerificationMessage(
                "verifica impegno: foo",
            ),
        )
        assertFalse(
            CommitmentFollowUpPolicy.isCommitmentVerificationMessage(
                "Verifica uscita — scrivania libera?",
            ),
        )
    }

    @Test
    fun `buildVerificationMessage prefixes goal`() {
        assertEquals(
            "Verifica impegno: Inviare il documento",
            CommitmentFollowUpPolicy.buildVerificationMessage("Inviare il documento"),
        )
    }

    @Test
    fun `followUpDueAt adds sixty minutes`() {
        val primary = 1_000_000L
        assertEquals(
            primary + CommitmentFollowUpPolicy.FOLLOW_UP_DELAY_MS,
            CommitmentFollowUpPolicy.followUpDueAt(primary),
        )
    }

    @Test
    fun `canReRemind allows only one rememorize`() {
        assertTrue(CommitmentFollowUpPolicy.canReRemind(0))
        assertFalse(CommitmentFollowUpPolicy.canReRemind(1))
        assertFalse(CommitmentFollowUpPolicy.canReRemind(2))
    }
}
