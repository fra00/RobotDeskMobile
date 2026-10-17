package com.example.mydeskrobot.data.health

import android.util.Log
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityClient

/**
 * Prefers exercise sessions from a paired PROXY phone; falls back to local Health Connect.
 */
class RemotePreferringExerciseReader(
    private val local: HealthConnectExerciseReader,
    private val hasPairedProxy: suspend () -> Boolean,
    private val remoteFetch: suspend (fromMs: Long, toMs: Long) -> List<HealthConnectExerciseSession>?,
) : HealthConnectExerciseReader {

    constructor(
        local: HealthConnectExerciseReader,
        modeRepository: DeviceModeRepository,
        capabilityClient: PhoneCapabilityClient,
    ) : this(
        local = local,
        hasPairedProxy = { modeRepository.primaryCapabilityProxy() != null },
        remoteFetch = { from, to -> capabilityClient.fetchExercises(from, to) },
    )

    override fun isAvailable(): Boolean {
        if (local.isAvailable()) return true
        return runCatching {
            kotlinx.coroutines.runBlocking { hasPairedProxy() }
        }.getOrDefault(false)
    }

    override suspend fun hasExerciseReadPermission(): Boolean {
        if (hasPairedProxy()) {
            // Phone-side permission is enforced by the proxy handler; attempt remote read.
            return true
        }
        return local.hasExerciseReadPermission()
    }

    override suspend fun readSessions(startTimeMs: Long, endTimeMs: Long): List<HealthConnectExerciseSession> {
        val remote = runCatching {
            remoteFetch(startTimeMs, endTimeMs)
        }.getOrElse { e ->
            Log.w(TAG, "Remote exercises failed", e)
            null
        }
        if (remote != null) {
            Log.d(TAG, "Using remote exercises count=${remote.size}")
            return remote
        }
        return local.readSessions(startTimeMs, endTimeMs)
    }

    companion object {
        private const val TAG = "RemotePreferringHC"
    }
}
