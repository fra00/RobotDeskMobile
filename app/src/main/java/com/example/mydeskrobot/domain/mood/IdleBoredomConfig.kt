package com.example.mydeskrobot.domain.mood

/**
 * Timing for situational boredom relief (look-around → distraction).
 * Not exposed in settings UI (v1).
 */
data class IdleBoredomConfig(
    /** Minutes of mild relief after look-around before a symbolic distraction may start. */
    val lookAroundReliefMinutes: Int = DEFAULT_LOOK_AROUND_RELIEF_MINUTES,
    /** Duration of the symbolic distraction overlay. */
    val distractionMinutes: Int = DEFAULT_DISTRACTION_MINUTES,
) {
    companion object {
        const val DEFAULT_LOOK_AROUND_RELIEF_MINUTES = 5
        const val DEFAULT_DISTRACTION_MINUTES = 8
    }
}
