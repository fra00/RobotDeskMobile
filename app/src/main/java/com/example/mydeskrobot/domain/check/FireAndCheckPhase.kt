package com.example.mydeskrobot.domain.check

enum class FireAndCheckPhase {
    /** Primary action not fired yet. */
    SCHEDULED,
    /** Primary fired; waiting for verification step. */
    AWAITING_VERIFICATION,
    /** Verification due has passed (or missing) — loop still open until handled/deleted. */
    CHECK_PENDING,
}

/**
 * Derives UI phase from stored due/fired timestamps.
 * [CHECK_PENDING] does **not** auto-close the loop when the due time is skipped.
 */
fun resolveFireAndCheckPhase(
    nowMillis: Long,
    primaryDueAtMillis: Long?,
    verificationDueAtMillis: Long?,
    primaryFiredAtMillis: Long?,
): FireAndCheckPhase = when {
    primaryFiredAtMillis == null &&
        primaryDueAtMillis != null &&
        nowMillis < primaryDueAtMillis -> FireAndCheckPhase.SCHEDULED
    verificationDueAtMillis != null &&
        nowMillis < verificationDueAtMillis -> FireAndCheckPhase.AWAITING_VERIFICATION
    else -> FireAndCheckPhase.CHECK_PENDING
}
