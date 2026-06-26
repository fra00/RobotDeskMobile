package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.MemorySettingsRepository

/**
 * Verifies cognitive projections after operational writes; retries once on mismatch.
 */
class MemoryProjectionGuard(
    private val repository: UnifiedMemoryRepository,
    private val settingsRepository: MemorySettingsRepository?,
) {

    suspend fun projectAndVerify(
        externalRef: String,
        expectedActive: Boolean,
        write: suspend () -> Unit,
    ): ProjectionResult {
        return runAttempt(externalRef, expectedActive, write, isRetry = false)
    }

    private suspend fun runAttempt(
        externalRef: String,
        expectedActive: Boolean,
        write: suspend () -> Unit,
        isRetry: Boolean,
    ): ProjectionResult {
        return try {
            write()
            if (repository.verifyProjection(externalRef, expectedActive)) {
                ProjectionResult.Success
            } else if (!isRetry) {
                logWarning("Projection verify failed for $externalRef (active=$expectedActive), retrying once")
                runAttempt(externalRef, expectedActive, write, isRetry = true)
            } else {
                recordDrift(externalRef, expectedActive)
                ProjectionResult.Drift(externalRef)
            }
        } catch (error: Exception) {
            if (!isRetry) {
                logWarning("Projection write failed for $externalRef, retrying once", error)
                runAttempt(externalRef, expectedActive, write, isRetry = true)
            } else {
                recordDrift(externalRef, expectedActive)
                logWarning("Projection write failed after retry for $externalRef", error)
                ProjectionResult.Drift(externalRef)
            }
        }
    }

    private suspend fun recordDrift(externalRef: String, expectedActive: Boolean) {
        logWarning("Projection drift: externalRef=$externalRef expectedActive=$expectedActive")
        settingsRepository?.recordProjectionDrift()
    }

    private fun logWarning(message: String, error: Throwable? = null) {
        runCatching {
            if (error != null) {
                android.util.Log.w(TAG, message, error)
            } else {
                android.util.Log.w(TAG, message)
            }
        }
    }

    sealed class ProjectionResult {
        data object Success : ProjectionResult()
        data class Drift(val externalRef: String) : ProjectionResult()
    }

    companion object {
        private const val TAG = "MemoryProjectionGuard"
    }
}
