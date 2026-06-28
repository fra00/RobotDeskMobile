package com.example.mydeskrobot.integration.body

/**
 * Closed gesture sequence: optional head neutralize before/after, timed steps.
 */
data class BodyChoreography(
    val steps: List<BodyMove>,
    val holdPeakMs: Long = 0L,
    val normalizeHeadBefore: Boolean = true,
    val returnHeadAfter: Boolean = false,
) {
    companion object {
        fun fromMoves(
            moves: List<BodyMove>,
            normalizeHeadBefore: Boolean = true,
            returnHeadAfter: Boolean = false,
        ): BodyChoreography = BodyChoreography(
            steps = moves,
            normalizeHeadBefore = normalizeHeadBefore,
            returnHeadAfter = returnHeadAfter,
        )
    }
}
