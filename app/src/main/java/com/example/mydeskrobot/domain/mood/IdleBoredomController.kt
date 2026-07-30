package com.example.mydeskrobot.domain.mood

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * In-memory state machine for situational boredom: look-around relief → symbolic distraction.
 * Does not modify wellbeing valence.
 */
class IdleBoredomController(
    private val config: IdleBoredomConfig = IdleBoredomConfig(),
    private val random: Random = Random.Default,
) {
    private val _phase = MutableStateFlow<IdleBoredomPhase>(IdleBoredomPhase.None)
    val phase: StateFlow<IdleBoredomPhase> = _phase.asStateFlow()

    fun isSuppressingIdleBoredom(): Boolean = _phase.value !is IdleBoredomPhase.None

    /** Look-around and new distractions are blocked while relieved or distracted. */
    fun blocksLookAround(): Boolean = isSuppressingIdleBoredom()

    fun currentDistractionKind(): IdleDistractionKind? =
        (_phase.value as? IdleBoredomPhase.Distracted)?.kind

    /**
     * Enter mild relief after a completed look-around.
     * @return true if phase changed to [IdleBoredomPhase.RelievedAfterLookAround]
     */
    fun onLookAroundCompleted(nowMs: Long): Boolean {
        if (_phase.value !is IdleBoredomPhase.None) return false
        val until = nowMs + config.lookAroundReliefMinutes.coerceAtLeast(1) * 60_000L
        _phase.value = IdleBoredomPhase.RelievedAfterLookAround(untilMs = until)
        return true
    }

    /**
     * Advance timers: start distraction after relief, or end distraction when elapsed.
     *
     * @param stillIdleEligible true when user has not interacted since look-around (clocks still high)
     * @param allowNewDistraction false at night / silent robot context
     */
    fun tick(
        nowMs: Long,
        stillIdleEligible: Boolean,
        allowNewDistraction: Boolean,
    ): IdleBoredomTickResult {
        return when (val current = _phase.value) {
            is IdleBoredomPhase.None -> IdleBoredomTickResult.Unchanged

            is IdleBoredomPhase.RelievedAfterLookAround -> {
                if (nowMs < current.untilMs) return IdleBoredomTickResult.Unchanged
                if (!stillIdleEligible || !allowNewDistraction) {
                    _phase.value = IdleBoredomPhase.None
                    return IdleBoredomTickResult.Cleared
                }
                val kind = IdleDistractionKind.entries[random.nextInt(IdleDistractionKind.entries.size)]
                val endsAt = nowMs + config.distractionMinutes.coerceAtLeast(1) * 60_000L
                _phase.value = IdleBoredomPhase.Distracted(kind = kind, endsAtMs = endsAt)
                IdleBoredomTickResult.StartedDistraction(kind)
            }

            is IdleBoredomPhase.Distracted -> {
                if (nowMs < current.endsAtMs) return IdleBoredomTickResult.Unchanged
                _phase.value = IdleBoredomPhase.None
                IdleBoredomTickResult.DistractionEnded
            }
        }
    }

    /** Voice / night / mic-off: drop relief or distraction without valence change. */
    fun interrupt(): IdleBoredomTickResult {
        val previous = _phase.value
        if (previous is IdleBoredomPhase.None) return IdleBoredomTickResult.Unchanged
        _phase.value = IdleBoredomPhase.None
        return when (previous) {
            is IdleBoredomPhase.Distracted -> IdleBoredomTickResult.DistractionEnded
            is IdleBoredomPhase.RelievedAfterLookAround -> IdleBoredomTickResult.Cleared
            is IdleBoredomPhase.None -> IdleBoredomTickResult.Unchanged
        }
    }

    /**
     * Debug/UI: jump straight into a symbolic distraction (bypasses look-around and relief).
     * Does not change wellbeing valence.
     */
    fun forceDistraction(kind: IdleDistractionKind, nowMs: Long): IdleBoredomTickResult {
        val endsAt = nowMs + config.distractionMinutes.coerceAtLeast(1) * 60_000L
        _phase.value = IdleBoredomPhase.Distracted(kind = kind, endsAtMs = endsAt)
        return IdleBoredomTickResult.StartedDistraction(kind)
    }
}
