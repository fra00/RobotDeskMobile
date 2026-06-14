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
 * Single writer for persistent [RobotMood] (wellbeing valence) and in-memory [EphemeralExpression].
 */
class MoodManager(
    private val repository: MoodRepository,
    private val engine: MoodEngine = MoodEngine(),
    private val scope: CoroutineScope,
) {
    private val _currentMood = MutableStateFlow(RobotMood.NEUTRAL)
    val currentMood: StateFlow<RobotMood> = _currentMood.asStateFlow()

    private val _ephemeralExpression = MutableStateFlow<EphemeralExpression?>(null)
    val ephemeralExpression: StateFlow<EphemeralExpression?> = _ephemeralExpression.asStateFlow()

    private var lastInteractionTime: Long = System.currentTimeMillis()

    suspend fun initialize() {
        val stored = repository.load()
        _currentMood.value = stored
        Log.d(
            TAG,
            "Mood initialized: valence=${stored.valence} ${stored.baseEmotion} (${stored.intensity})",
        )
    }

    fun onTrigger(trigger: MoodTrigger) {
        val current = _currentMood.value
        val now = System.currentTimeMillis()

        if (trigger is MoodTrigger.PositiveInteraction ||
            trigger is MoodTrigger.UserApology ||
            trigger is MoodTrigger.TaskCompletedUseful
        ) {
            lastInteractionTime = now
        }

        val newMood = engine.evaluate(current, trigger, now)
        applyMoodIfChanged(current, newMood)
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
        clearExpiredEphemeral(now)
    }

    fun touchLastInteraction() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun recordPositiveInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        onTrigger(MoodTrigger.PositiveInteraction)
    }

    fun recordNegativeInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        onTrigger(MoodTrigger.NegativeInteraction)
    }

    fun recordTaskCompletedUseful() {
        lastInteractionTime = System.currentTimeMillis()
        onTrigger(MoodTrigger.TaskCompletedUseful)
    }

    fun recordApology() {
        lastInteractionTime = System.currentTimeMillis()
        onTrigger(MoodTrigger.UserApology)
    }

    fun recordEyePoke(tier: Int, count: Int) {
        onTrigger(MoodTrigger.EyePoked(tier, count))
    }

    /** LLM JSON emotion: ephemeral only — does not change persistent valence. */
    fun setEphemeralExpression(emotion: RobotEmotion?) {
        val now = System.currentTimeMillis()
        _ephemeralExpression.value = EphemeralExpressionPolicy.create(emotion, now)
    }

    fun clearExpiredEphemeral(now: Long = System.currentTimeMillis()) {
        val current = _ephemeralExpression.value ?: return
        if (!current.isActive(now)) {
            _ephemeralExpression.value = null
        }
    }

    fun getIdleMinutes(): Long =
        (System.currentTimeMillis() - lastInteractionTime) / 60_000L

    private fun applyMoodIfChanged(previous: RobotMood, newMood: RobotMood?) {
        if (newMood != null && newMood != previous) {
            Log.i(
                TAG,
                "Wellbeing: valence ${previous.valence} → ${newMood.valence} " +
                    "(${previous.baseEmotion} → ${newMood.baseEmotion})",
            )
            _currentMood.value = newMood
            scope.launch { repository.save(newMood) }
        }
    }

    companion object {
        private const val TAG = "MoodManager"
    }
}
