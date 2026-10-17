package com.example.mydeskrobot.data.health

/**
 * Reads exercise sessions from Health Connect (or a test fake).
 */
interface HealthConnectExerciseReader {
    /** True when Health Connect is installed and usable on this device. */
    fun isAvailable(): Boolean

    /** True when the app already holds exercise read permission. */
    suspend fun hasExerciseReadPermission(): Boolean

    /**
     * Exercise sessions whose start time falls in [[startTimeMs], [endTimeMs]].
     * Empty when unavailable or permission missing (no throw).
     */
    suspend fun readSessions(startTimeMs: Long, endTimeMs: Long): List<HealthConnectExerciseSession>
}
