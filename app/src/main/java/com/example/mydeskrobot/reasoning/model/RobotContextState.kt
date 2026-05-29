package com.example.mydeskrobot.reasoning.model

/**
 * Persisted robot interaction context (desk robot only).
 */
data class RobotContextState(
    val profile: RobotProfile = RobotProfile.NORMAL,
    val notificationMode: NotificationMode = NotificationMode.NORMAL,
    val sessionOnly: Boolean = false,
    val validUntilEpochMs: Long? = null,
    val windowStartMinutes: Int? = null,
    val windowEndMinutes: Int? = null,
) {
    val isNormal: Boolean
        get() = profile == RobotProfile.NORMAL &&
            notificationMode == NotificationMode.NORMAL &&
            validUntilEpochMs == null &&
            windowStartMinutes == null &&
            windowEndMinutes == null

    companion object {
        val NORMAL = RobotContextState()
    }
}
