package com.example.mydeskrobot.integration.body

/**
 * Subtle head oscillation pattern while the robot speaks (conversational fidget).
 * All positions are absolute degrees from neutral on head joints only.
 */
object SpeakingMicroMoves {

    const val SPEED = 18
    const val MOVE_HOLD_MS = 420L
    private const val ROLL_EVERY_N_STEPS = 4

    private val tiltCycle = intArrayOf(3, -2, 4, -1, 2, -3, 1, 0)

    fun headTiltAt(step: Int): Int = tiltCycle[step % tiltCycle.size]

    fun headRollAt(step: Int): Int? {
        if (step % ROLL_EVERY_N_STEPS != ROLL_EVERY_N_STEPS - 1) return null
        return when ((step / ROLL_EVERY_N_STEPS) % 3) {
            0 -> 2
            1 -> -2
            else -> 0
        }
    }
}
