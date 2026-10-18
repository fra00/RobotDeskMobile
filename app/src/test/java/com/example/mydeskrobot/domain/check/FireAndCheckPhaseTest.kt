package com.example.mydeskrobot.domain.check

import org.junit.Assert.assertEquals
import org.junit.Test

class FireAndCheckPhaseTest {

    private val now = 1_000_000L

    @Test
    fun scheduled_whenPrimaryStillInFuture() {
        assertEquals(
            FireAndCheckPhase.SCHEDULED,
            resolveFireAndCheckPhase(
                nowMillis = now,
                primaryDueAtMillis = now + 60_000L,
                verificationDueAtMillis = now + 120_000L,
                primaryFiredAtMillis = null,
            ),
        )
    }

    @Test
    fun awaiting_whenVerificationStillInFuture() {
        assertEquals(
            FireAndCheckPhase.AWAITING_VERIFICATION,
            resolveFireAndCheckPhase(
                nowMillis = now,
                primaryDueAtMillis = now - 60_000L,
                verificationDueAtMillis = now + 30_000L,
                primaryFiredAtMillis = now - 50_000L,
            ),
        )
    }

    @Test
    fun pending_whenVerificationDuePassed_staysOpen() {
        assertEquals(
            FireAndCheckPhase.CHECK_PENDING,
            resolveFireAndCheckPhase(
                nowMillis = now,
                primaryDueAtMillis = now - 86_400_000L * 30,
                verificationDueAtMillis = now - 86_400_000L * 29,
                primaryFiredAtMillis = now - 86_400_000L * 30,
            ),
        )
    }

    @Test
    fun pending_whenVerificationDueMissingAfterPrimaryPassed() {
        assertEquals(
            FireAndCheckPhase.CHECK_PENDING,
            resolveFireAndCheckPhase(
                nowMillis = now,
                primaryDueAtMillis = now - 1_000L,
                verificationDueAtMillis = null,
                primaryFiredAtMillis = null,
            ),
        )
    }
}
