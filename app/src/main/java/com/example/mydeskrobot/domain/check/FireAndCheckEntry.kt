package com.example.mydeskrobot.domain.check

/**
 * User-visible pending fire-and-check loop (verification after an action).
 */
data class FireAndCheckEntry(
    val id: Long,
    val kind: FireAndCheckKind = FireAndCheckKind.PHYSICAL,
    val triggerReason: String,
    val checkGoal: String?,
    val primaryMessage: String,
    val verificationMessage: String?,
    val primaryDueAtMillis: Long?,
    val verificationDueAtMillis: Long?,
    val phase: FireAndCheckPhase,
    val reRemindCount: Int = 0,
) {
    /** Text shown when the user taps the indicator. */
    fun detailText(): String {
        checkGoal?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        verificationMessage?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        triggerReason.trim().takeIf { it.isNotBlank() }?.let { return it }
        return primaryMessage
    }

    val isCommitment: Boolean
        get() = kind == FireAndCheckKind.COMMITMENT
}
