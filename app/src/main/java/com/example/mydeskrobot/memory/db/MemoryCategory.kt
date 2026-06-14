package com.example.mydeskrobot.memory.db

enum class MemoryCategory {
    IDENTITY,
    PREFERENCE,
    ROUTINE,
    FACT,
    /** Short-lived contextual notes for autonomous heartbeat (robot-internal). */
    OBSERVATION,
    /** Active autonomous goal the robot pursues across heartbeats (robot-internal). */
    INTENT,
    /** Emerging pattern not yet promoted to ROUTINE (robot-internal). */
    PATTERN,
    ;

    companion object {
        val USER_FACING: Set<MemoryCategory> = setOf(IDENTITY, PREFERENCE, ROUTINE, FACT)
        val ROBOT_INTERNAL: Set<MemoryCategory> = setOf(OBSERVATION, INTENT, PATTERN)

        fun isUserFacing(category: MemoryCategory): Boolean = category in USER_FACING
        fun isRobotInternal(category: MemoryCategory): Boolean = category in ROBOT_INTERNAL
    }
}
