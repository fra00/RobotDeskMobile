package com.example.mydeskrobot.integration.body

/**
 * Physical body command derived from [com.example.mydeskrobot.domain.mood.RobotMood].
 */
sealed class BodyMove {
    data class Joint(
        val joint: BodyJoint,
        val delta: Int? = null,
        val position: Int? = null,
        val speed: Int? = null,
        val delayAfterMs: Long = 0L,
    ) : BodyMove()

    data class Home(
        val speed: Int? = null,
        val delayAfterMs: Long = 0L,
    ) : BodyMove()

    /** Center pose if needed, then slight head lower — executed with status check in [BodyExpressionController]. */
    data object SleepPose : BodyMove()
}
