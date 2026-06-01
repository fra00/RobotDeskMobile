package com.example.mydeskrobot.domain.mood

import android.util.Log
import com.example.mydeskrobot.data.mood.MoodRepository
import com.example.mydeskrobot.domain.model.RobotEmotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages the robot's autonomous mood state (single writer for [RobotMood]).
 */
class MoodManager(
    private val repository: MoodRepository,
    private val engine: MoodEngine = MoodEngine(),
    private val scope: CoroutineScope,
) {
    private val _currentMood = MutableStateFlow(RobotMood.NEUTRAL)
    val currentMood: StateFlow<RobotMood> = _currentMood.asStateFlow()

    private var lastInteractionTime: Long = System.currentTimeMillis()

    suspend fun initialize() {
        val stored = repository.load()
        _currentMood.value = stored
        Log.d(TAG, "Mood initialized: ${stored.baseEmotion} (${stored.intensity})")
    }

    fun onTrigger(trigger: MoodTrigger) {
        val current = _currentMood.value
        val now = System.currentTimeMillis()

        if (trigger is MoodTrigger.PositiveInteraction || trigger is MoodTrigger.UserApology) {
            lastInteractionTime = now
        }

        val newMood = engine.evaluate(current, trigger, now)
        applyMoodIfChanged(current, newMood)
    }

    fun onLlmEmotion(emotion: RobotEmotion) {
        onTrigger(MoodTrigger.LlmEmotion(emotion))
    }

    fun checkIdleTransition() {
        val idleMinutes = (System.currentTimeMillis() - lastInteractionTime) / 60_000L
        onTrigger(MoodTrigger.IdleTime(idleMinutes))
    }

    fun checkDecay() {
        val current = _currentMood.value
        val now = System.currentTimeMillis()
        val decayed = engine.checkDecay(current, now)
        applyMoodIfChanged(current, decayed)
    }

    /** Updates idle timer only; does not change mood (neutral questions while annoyed). */
    fun touchLastInteraction() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun recordPositiveInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        onTrigger(MoodTrigger.PositiveInteraction)
    }

    fun recordApology() {
        lastInteractionTime = System.currentTimeMillis()
        onTrigger(MoodTrigger.UserApology)
    }

    fun recordEyePoke(tier: Int, count: Int) {
        onTrigger(MoodTrigger.EyePoked(tier, count))
    }

    /** Sync persistent mood with emotion declared in the assistant JSON reply. */
    fun applyAssistantDeclaredEmotion(emotion: RobotEmotion) {
        onTrigger(MoodTrigger.AssistantDeclaredEmotion(emotion))
    }

    fun getIdleMinutes(): Long =
        (System.currentTimeMillis() - lastInteractionTime) / 60_000L

    private fun applyMoodIfChanged(previous: RobotMood, newMood: RobotMood?) {
        if (newMood != null && newMood != previous) {
            Log.i(TAG, "Mood transition: ${previous.baseEmotion} → ${newMood.baseEmotion}")
            _currentMood.value = newMood
            scope.launch { repository.save(newMood) }
        }
    }

    companion object {
        private const val TAG = "MoodManager"
    }
}
