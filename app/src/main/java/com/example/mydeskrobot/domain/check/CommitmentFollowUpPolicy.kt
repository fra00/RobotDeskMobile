package com.example.mydeskrobot.domain.check

/**
 * Pure rules for urgent commitment follow-up (no Android).
 */
object CommitmentFollowUpPolicy {

    /** Delay from primary due time to “Verifica impegno” ask. */
    const val FOLLOW_UP_DELAY_MS: Long = 60L * 60_000L

    /** When speak gate blocks, push verification later. */
    const val DEFER_INTERVAL_MS: Long = 20L * 60_000L

    /** Max times the user can ask “ricordami ancora” for the same loop. */
    const val MAX_RE_REMIND: Int = 1

    const val VERIFICATION_PREFIX: String = "Verifica impegno:"

    fun isCommitmentVerificationMessage(message: String): Boolean {
        val trimmed = message.trim()
        return trimmed.startsWith(VERIFICATION_PREFIX, ignoreCase = true)
    }

    fun buildVerificationMessage(goalOrMessage: String): String {
        val goal = goalOrMessage.trim().ifBlank { "l'impegno" }
        val withoutPrefix = if (isCommitmentVerificationMessage(goal)) {
            goal.removePrefix(VERIFICATION_PREFIX).trim()
                .removePrefix(VERIFICATION_PREFIX).trim()
                .ifBlank { goal }
        } else {
            goal
        }
        return "$VERIFICATION_PREFIX $withoutPrefix"
    }

    fun followUpDueAt(primaryDueAtMillis: Long): Long =
        primaryDueAtMillis + FOLLOW_UP_DELAY_MS

    fun deferDueAt(nowMillis: Long = System.currentTimeMillis()): Long =
        nowMillis + DEFER_INTERVAL_MS

    fun canReRemind(reRemindCount: Int): Boolean =
        reRemindCount < MAX_RE_REMIND
}
