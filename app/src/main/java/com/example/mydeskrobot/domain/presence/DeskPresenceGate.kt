package com.example.mydeskrobot.domain.presence

/**
 * Decides whether proactive bot interaction is allowed based on desk occupancy.
 */
object DeskPresenceGate {
    private const val RECENT_INTERACTION_GRACE_MS = 15 * 60 * 1000L

    fun allowsProactiveInteraction(
        occupancy: DeskOccupancy,
        lastInteractionMillis: Long,
        monitorEnabled: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!monitorEnabled) return true

        return when (occupancy.state) {
            DeskOccupancyState.PRESENT -> true
            DeskOccupancyState.ABSENT -> false
            DeskOccupancyState.UNKNOWN -> false
            DeskOccupancyState.UNCERTAIN -> {
                if (lastInteractionMillis <= 0L) return false
                (now - lastInteractionMillis) < RECENT_INTERACTION_GRACE_MS
            }
        }
    }
}
