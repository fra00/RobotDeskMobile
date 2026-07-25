package com.example.mydeskrobot.domain.mood

/**
 * Situational boredom relief cycle (idle reason / timer only — never valence).
 */
sealed interface IdleBoredomPhase {
    data object None : IdleBoredomPhase

    /** After look-around: idle reason cleared; re-idle suppressed until [untilMs]. */
    data class RelievedAfterLookAround(val untilMs: Long) : IdleBoredomPhase

    /** Symbolic distraction overlay until [endsAtMs]. */
    data class Distracted(
        val kind: IdleDistractionKind,
        val endsAtMs: Long,
    ) : IdleBoredomPhase
}

sealed interface IdleBoredomTickResult {
    data object Unchanged : IdleBoredomTickResult
    data object Cleared : IdleBoredomTickResult
    data class StartedDistraction(val kind: IdleDistractionKind) : IdleBoredomTickResult
    data object DistractionEnded : IdleBoredomTickResult
}
