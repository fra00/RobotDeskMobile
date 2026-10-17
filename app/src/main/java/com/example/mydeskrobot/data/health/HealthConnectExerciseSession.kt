package com.example.mydeskrobot.data.health

/**
 * Platform-agnostic exercise session from Health Connect (or a fake reader in tests).
 */
data class HealthConnectExerciseSession(
    /** Stable HC metadata id used for [sourceChannel] dedup. */
    val metadataId: String,
    /** [androidx.health.connect.client.records.ExerciseSessionRecord] exerciseType int. */
    val exerciseType: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String? = null,
)
