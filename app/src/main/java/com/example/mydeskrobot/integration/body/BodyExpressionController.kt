package com.example.mydeskrobot.integration.body

import android.util.Log
import com.example.mydeskrobot.data.body.BodySettings
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.RobotMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Executes mood-driven and ephemeral body presets (SSOT alongside eyes and LLM prompt).
 */
class BodyExpressionController(
    private val scope: CoroutineScope,
    private val settingsProvider: suspend () -> BodySettings,
) {
    private val executor = BodyChoreographyExecutor()
    private var expressionJob: Job? = null
    private var speakingJob: Job? = null

    fun onMoodTransition(
        previous: RobotMood,
        current: RobotMood,
        context: BodyExpressionContext,
    ) {
        if (previous == current) return
        if (!context.allowsMoodExpression(current.reason)) return

        val choreography = BodyExpressionMapper.resolve(previous, current) ?: return
        runChoreography(choreography, "Mood ${current.baseEmotion} (${current.reason})")
    }

    fun onEphemeralExpression(
        emotion: RobotEmotion,
        intensity: Float,
        context: BodyExpressionContext,
    ) {
        if (!context.allowsEphemeralGesture()) return

        val choreography = EmotionGestureMapper.resolve(emotion, intensity) ?: return
        runChoreography(choreography, "Ephemeral $emotion")
    }

    fun restoreHeadNeutralUnlessSleeping(baseEmotion: RobotEmotion) {
        if (baseEmotion == RobotEmotion.SLEEPING) return
        expressionJob?.cancel()
        expressionJob = scope.launch {
            val client = BodyApiClient.createIfConfigured(settingsProvider()) ?: return@launch
            executor.restoreHeadNeutral(client)
            Log.d(TAG, "Head restored to neutral after ephemeral expiry")
        }
    }

    fun executeMicroTick(
        choreography: BodyChoreography,
        context: BodyExpressionContext,
    ) {
        if (!context.allowsMicroTick()) return
        runChoreography(choreography, "Heartbeat micro tick")
    }

    /**
     * Subtle head micro-movements while TTS is playing (conversational body language).
     * Waits for any in-flight emotion gesture, then loops until [isStillSpeaking] is false or stopped.
     */
    fun startSpeakingMicroMoves(
        context: BodyExpressionContext,
        isStillSpeaking: () -> Boolean,
    ) {
        if (!context.allowsSpeakingMicroMoves()) return
        speakingJob?.cancel()
        speakingJob = scope.launch {
            val client = BodyApiClient.createIfConfigured(settingsProvider()) ?: return@launch
            expressionJob?.join()
            if (!isStillSpeaking()) return@launch
            if (!HeadNeutralizer.neutralizeHead(client)) {
                Log.w(TAG, "Speaking micro-moves: head neutralize failed, continuing")
            }
            var step = 0
            while (isActive && isStillSpeaking()) {
                val tilt = SpeakingMicroMoves.headTiltAt(step)
                when (
                    val tiltResult = client.moveJoint(
                        joint = BodyJoint.HEAD_TILT,
                        position = tilt,
                        speed = SpeakingMicroMoves.SPEED,
                    )
                ) {
                    is BodyApiResult.Error -> {
                        Log.w(TAG, "Speaking micro-move tilt failed: ${tiltResult.message}")
                        return@launch
                    }
                    is BodyApiResult.Success -> Unit
                }
                delay(SpeakingMicroMoves.MOVE_HOLD_MS)
                if (!isStillSpeaking()) break

                val roll = SpeakingMicroMoves.headRollAt(step)
                if (roll != null) {
                    when (
                        val rollResult = client.moveJoint(
                            joint = BodyJoint.HEAD_ROLL,
                            position = roll,
                            speed = SpeakingMicroMoves.SPEED,
                        )
                    ) {
                        is BodyApiResult.Error -> {
                            Log.w(TAG, "Speaking micro-move roll failed: ${rollResult.message}")
                            return@launch
                        }
                        is BodyApiResult.Success -> Unit
                    }
                    delay(SpeakingMicroMoves.MOVE_HOLD_MS)
                }
                step++
            }
            Log.d(TAG, "Speaking micro-moves loop ended")
        }
    }

    fun stopSpeakingMicroMoves(baseEmotion: RobotEmotion) {
        speakingJob?.cancel()
        speakingJob = null
        if (baseEmotion == RobotEmotion.SLEEPING) return
        scope.launch {
            val client = BodyApiClient.createIfConfigured(settingsProvider()) ?: return@launch
            executor.restoreHeadNeutral(client)
            Log.d(TAG, "Head restored to neutral after speaking")
        }
    }

    fun cancel() {
        expressionJob?.cancel()
        expressionJob = null
        speakingJob?.cancel()
        speakingJob = null
    }

    private fun runChoreography(choreography: BodyChoreography, logLabel: String) {
        if (choreography.steps.isEmpty()) return
        expressionJob?.cancel()
        expressionJob = scope.launch {
            val client = BodyApiClient.createIfConfigured(settingsProvider()) ?: return@launch
            executor.execute(client, choreography, logLabel)
        }
    }

    companion object {
        private const val TAG = "BodyExpression"
    }
}
