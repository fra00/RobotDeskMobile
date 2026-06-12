package com.example.mydeskrobot.integration.body

import android.util.Log
import com.example.mydeskrobot.data.body.BodySettings
import com.example.mydeskrobot.domain.mood.RobotMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Executes mood-driven body presets when [RobotMood] changes (SSOT alongside eyes and LLM prompt).
 */
class BodyExpressionController(
    private val scope: CoroutineScope,
    private val settingsProvider: suspend () -> BodySettings,
) {
    private var expressionJob: Job? = null

    fun onMoodTransition(
        previous: RobotMood,
        current: RobotMood,
        context: BodyExpressionContext,
    ) {
        if (previous == current) return
        if (!context.allowsExpression(current.reason)) return

        val moves = BodyExpressionMapper.resolve(previous, current)
        if (moves.isEmpty()) return

        expressionJob?.cancel()
        expressionJob = scope.launch {
            val client = BodyApiClient.createIfConfigured(settingsProvider()) ?: return@launch
            for (move in moves) {
                val delayAfterMs = when (move) {
                    is BodyMove.Joint -> move.delayAfterMs
                    is BodyMove.Home -> move.delayAfterMs
                    is BodyMove.SleepPose -> 0L
                }
                when (move) {
                    is BodyMove.SleepPose -> {
                        if (!executeSleepPose(client)) return@launch
                    }
                    is BodyMove.Joint -> {
                        val result = client.moveJoint(
                            joint = move.joint,
                            delta = move.delta,
                            position = move.position,
                            speed = move.speed,
                        )
                        if (result is BodyApiResult.Error) {
                            Log.w(TAG, "Mood body expression failed: ${result.message}")
                            return@launch
                        }
                    }
                    is BodyMove.Home -> {
                        val result = client.home(speed = move.speed)
                        if (result is BodyApiResult.Error) {
                            Log.w(TAG, "Mood body expression failed: ${result.message}")
                            return@launch
                        }
                    }
                }
                if (delayAfterMs > 0L) {
                    delay(delayAfterMs)
                }
            }
            Log.d(TAG, "Mood body expression done: ${current.baseEmotion} (${current.reason})")
        }
    }

    fun cancel() {
        expressionJob?.cancel()
        expressionJob = null
    }

    private suspend fun executeSleepPose(client: BodyApiClient): Boolean {
        val status = when (val result = client.getStatus()) {
            is BodyApiResult.Success -> result.data
            is BodyApiResult.Error -> {
                Log.w(TAG, "Sleep pose: status unavailable (${result.message}), applying home+tilt")
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
        private const val TAG = "BodyExpression"
    }
}
