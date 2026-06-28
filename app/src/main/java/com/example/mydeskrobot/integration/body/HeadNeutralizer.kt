package com.example.mydeskrobot.integration.body

/**
 * Centers head/display joints (not base_pan) before expressive gestures.
 */
object HeadNeutralizer {

    const val TOLERANCE_DEG = 4
    const val NEUTRAL_SPEED = 30

    val HEAD_JOINTS: Set<BodyJoint> = setOf(
        BodyJoint.HEAD_TILT,
        BodyJoint.HEAD_ROLL,
        BodyJoint.DISPLAY_PAN,
    )

    fun jointsNeedingNeutral(status: BodyStatus): Map<BodyJoint, Int> {
        val result = mutableMapOf<BodyJoint, Int>()
        HEAD_JOINTS.forEach { joint ->
            val position = status.joints[joint.apiName]?.position ?: 0
            if (kotlin.math.abs(position) > TOLERANCE_DEG) {
                result[joint] = 0
            }
        }
        return result
    }

    suspend fun neutralizeHead(client: BodyApiClient, speed: Int = NEUTRAL_SPEED): Boolean {
        val status = when (val result = client.getStatus()) {
            is BodyApiResult.Success -> result.data
            is BodyApiResult.Error -> return true
        }
        val targets = jointsNeedingNeutral(status)
        if (targets.isEmpty()) return true
        return when (client.moveJoints(targets, speed = speed)) {
            is BodyApiResult.Success -> true
            is BodyApiResult.Error -> false
        }
    }
}
