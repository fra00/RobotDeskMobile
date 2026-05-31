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
 * Manages the robot's autonomous mood state.
 * Coordinates between [MoodEngine] (rules) and [MoodRepository] (persistence).
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

        if (trigger is MoodTrigger.UserInteraction) {
            lastInteractionTime = now
        }

        val newMood = engine.evaluate(current, trigger, now)
        if (newMood != null && newMood != current) {
            Log.i(TAG, "Mood transition: ${current.baseEmotion} → ${newMood.baseEmotion} (trigger: $trigger)")
            _currentMood.value = newMood
            scope.launch { repository.save(newMood) }
        }
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
        if (decayed != null) {
            Log.i(TAG, "Mood decay: ${current.baseEmotion} → ${decayed.baseEmotion}")
            _currentMood.value = decayed
            scope.launch { repository.save(decayed) }
        }
    }

    fun recordInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        onTrigger(MoodTrigger.UserInteraction)
    }

    fun getIdleMinutes(): Long =
        (System.currentTimeMillis() - lastInteractionTime) / 60_000L

    companion object {
        private const val TAG = "MoodManager"
    }
}
