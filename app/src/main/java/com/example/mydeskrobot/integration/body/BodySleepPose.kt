package com.example.mydeskrobot.integration.body

/**
 * Sleep body pose: centered joints + slightly lowered head.
 */
object BodySleepPose {

    const val CENTER_TOLERANCE_DEG = 4
    const val SLEEP_HEAD_TILT_DEG = -10
    const val HEAD_TOLERANCE_DEG = 3
    const val HOME_SPEED = 25
    const val TILT_SPEED = 25
    const val DELAY_AFTER_HOME_MS = 400L

    fun isNearCenter(status: BodyStatus): Boolean =
        BodyJoint.entries.all { joint ->
            val position = status.joints[joint.apiName]?.position ?: 0
            kotlin.math.abs(position) <= CENTER_TOLERANCE_DEG
        }

    fun isHeadAtSleepTilt(status: BodyStatus): Boolean {
        val position = status.joints[BodyJoint.HEAD_TILT.apiName]?.position ?: 0
        return kotlin.math.abs(position - SLEEP_HEAD_TILT_DEG) <= HEAD_TOLERANCE_DEG
    }
}
