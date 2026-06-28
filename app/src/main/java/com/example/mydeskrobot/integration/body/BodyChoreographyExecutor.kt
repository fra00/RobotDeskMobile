package com.example.mydeskrobot.integration.body

import android.util.Log
import kotlinx.coroutines.delay

/**
 * Runs [BodyChoreography] sequences against ESP32 with head neutralize guards.
 */
class BodyChoreographyExecutor {

    suspend fun execute(
        client: BodyApiClient,
        choreography: BodyChoreography,
        logLabel: String,
    ): Boolean {
        if (choreography.steps.isEmpty()) return true

        if (choreography.normalizeHeadBefore) {
            if (!HeadNeutralizer.neutralizeHead(client)) {
                Log.w(TAG, "$logLabel: head neutralize before failed")
            }
        }

        for (move in choreography.steps) {
            val delayAfterMs = when (move) {
                is BodyMove.Joint -> move.delayAfterMs
                is BodyMove.Home -> move.delayAfterMs
                is BodyMove.SleepPose -> 0L
            }
            when (move) {
                is BodyMove.SleepPose -> {
                    if (!executeSleepPose(client)) return false
                }
                is BodyMove.Joint -> {
                    val result = client.moveJoint(
                        joint = move.joint,
                        delta = move.delta,
                        position = move.position,
                        speed = move.speed,
                    )
                    if (result is BodyApiResult.Error) {
                        Log.w(TAG, "$logLabel: joint move failed: ${result.message}")
                        return false
                    }
                }
                is BodyMove.Home -> {
                    val result = client.home(speed = move.speed)
                    if (result is BodyApiResult.Error) {
                        Log.w(TAG, "$logLabel: home failed: ${result.message}")
                        return false
                    }
                }
            }
            if (delayAfterMs > 0L) delay(delayAfterMs)
        }

        if (choreography.holdPeakMs > 0L) {
            delay(choreography.holdPeakMs)
        }

        if (choreography.returnHeadAfter) {
            if (!HeadNeutralizer.neutralizeHead(client)) {
                Log.w(TAG, "$logLabel: head neutralize after failed")
            }
        }

        Log.d(TAG, "$logLabel: choreography done")
        return true
    }

    suspend fun restoreHeadNeutral(client: BodyApiClient): Boolean =
        HeadNeutralizer.neutralizeHead(client)

    private suspend fun executeSleepPose(client: BodyApiClient): Boolean {
        val status = when (val result = client.getStatus()) {
            is BodyApiResult.Success -> result.data
            is BodyApiResult.Error -> {
                Log.w(TAG, "Sleep pose: status unavailable (${result.message})")
                null
            }
        }

        if (status == null || !BodySleepPose.isNearCenter(status)) {
            when (val home = client.home(speed = BodySleepPose.HOME_SPEED)) {
                is BodyApiResult.Error -> {
                    Log.w(TAG, "Sleep pose home failed: ${home.message}")
                    return false
                }
                is BodyApiResult.Success -> delay(BodySleepPose.DELAY_AFTER_HOME_MS)
            }
        }

        val tiltStatus = when (val result = client.getStatus()) {
            is BodyApiResult.Success -> result.data
            is BodyApiResult.Error -> status
        }
        if (tiltStatus == null || !BodySleepPose.isHeadAtSleepTilt(tiltStatus)) {
            when (
                val tilt = client.moveJoint(
                    joint = BodyJoint.HEAD_TILT,
                    position = BodySleepPose.SLEEP_HEAD_TILT_DEG,
                    speed = BodySleepPose.TILT_SPEED,
                )
            ) {
                is BodyApiResult.Error -> {
                    Log.w(TAG, "Sleep pose head tilt failed: ${tilt.message}")
                    return false
                }
                is BodyApiResult.Success -> Unit
            }
        }
        return true
    }

    companion object {
        private const val TAG = "BodyChoreography"
    }
}
