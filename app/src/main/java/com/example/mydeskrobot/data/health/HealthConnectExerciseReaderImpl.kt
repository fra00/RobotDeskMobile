package com.example.mydeskrobot.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Health Connect backed [HealthConnectExerciseReader]. No-ops when SDK unavailable or permission denied.
 */
class HealthConnectExerciseReaderImpl(
    context: Context,
) : HealthConnectExerciseReader {

    private val appContext = context.applicationContext

    override fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE

    override suspend fun hasExerciseReadPermission(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            val client = HealthConnectClient.getOrCreate(appContext)
            val granted = client.permissionController.getGrantedPermissions()
            READ_EXERCISE_PERMISSION in granted
        }.getOrElse { e ->
            Log.w(TAG, "Failed to check Health Connect permissions", e)
            false
        }
    }

    override suspend fun readSessions(startTimeMs: Long, endTimeMs: Long): List<HealthConnectExerciseSession> {
        if (!isAvailable()) return emptyList()
        if (!hasExerciseReadPermission()) return emptyList()
        return runCatching {
            val client = HealthConnectClient.getOrCreate(appContext)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(startTimeMs),
                        Instant.ofEpochMilli(endTimeMs),
                    ),
                ),
            )
            response.records.mapNotNull { record ->
                val id = record.metadata.id.trim().takeIf { it.isNotBlank() }
                    ?: fallbackId(record)
                HealthConnectExerciseSession(
                    metadataId = id,
                    exerciseType = record.exerciseType,
                    startTimeMs = record.startTime.toEpochMilli(),
                    endTimeMs = record.endTime.toEpochMilli(),
                    title = record.title,
                )
            }
        }.getOrElse { e ->
            Log.w(TAG, "Failed to read Health Connect exercise sessions", e)
            emptyList()
        }
    }

    private fun fallbackId(record: ExerciseSessionRecord): String =
        "local:${record.exerciseType}:${record.startTime.toEpochMilli()}:${record.endTime.toEpochMilli()}"

    companion object {
        private const val TAG = "HealthConnectReader"

        val READ_EXERCISE_PERMISSION: String =
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)

        val REQUIRED_PERMISSIONS: Set<String> = setOf(READ_EXERCISE_PERMISSION)
    }
}
