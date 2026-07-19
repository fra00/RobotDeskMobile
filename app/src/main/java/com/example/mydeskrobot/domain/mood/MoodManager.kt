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
    private val moodConfig: MoodConfig = MoodConfig(),
    private val turnMoodConfig: TurnMoodConfig = TurnMoodConfig(),
) {
    private val _currentMood = MutableStateFlow(RobotMood.NEUTRAL)
    val currentMood: StateFlow<RobotMood> = _currentMood.asStateFlow()

    private val _ephemeralExpression = MutableStateFlow<EphemeralExpression?>(null)
    val ephemeralExpression: StateFlow<EphemeralExpression?> = _ephemeralExpression.asStateFlow()

    private var lastInteractionTime: Long = System.currentTimeMillis()
    private var lastVoiceTurnTime: Long = System.currentTimeMillis()
    private var conversationSession: ConversationMoodSession = ConversationMoodSession()
    private var turnPromptHints: List<String> = emptyList()

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

        if (trigger is MoodTrigger.UserApology ||
            trigger is MoodTrigger.TaskCompletedUseful ||
            trigger is MoodTrigger.LlmEmotion ||
            trigger is MoodTrigger.VoiceTurnPresence ||
            trigger is MoodTrigger.ValenceDelta
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

    fun checkHotwordListeningIdle() {
        val idleMinutes = (System.currentTimeMillis() - lastVoiceTurnTime) / 60_000L
        onTrigger(MoodTrigger.HotwordListeningIdle(idleMinutes))
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

    fun recordTaskCompletedUseful() {
        lastInteractionTime = System.currentTimeMillis()
        onTrigger(MoodTrigger.TaskCompletedUseful)
    }

    fun recordEyePoke(tier: Int, count: Int) {
        onTrigger(MoodTrigger.EyePoked(tier, count))
    }

    fun recordVoiceTurn(phrase: String) {
        val now = System.currentTimeMillis()
        lastVoiceTurnTime = now
        lastInteractionTime = now
        val (session, signals) = TurnMoodEvaluator.evaluateUserTurn(
            phrase = phrase,
            session = conversationSession,
            config = turnMoodConfig,
            now = now,
        )
        conversationSession = session
        turnPromptHints = signals.promptHints
        signals.triggers.forEach { onTrigger(it) }
    }

    fun recordToolSuccessInSession() {
        conversationSession = conversationSession.onToolSuccess(System.currentTimeMillis())
    }

    fun resetConversationSession() {
        conversationSession = ConversationMoodSession.reset()
        turnPromptHints = emptyList()
        val now = System.currentTimeMillis()
        lastVoiceTurnTime = now
        lastInteractionTime = now
    }

    fun currentPromptHints(): List<String> = turnPromptHints

    /** Ephemeral eyes only — no persistent valence (micro-tick, internal UI). */
    fun setEphemeralExpression(emotion: RobotEmotion?) {
        val mood = _currentMood.value
        val now = System.currentTimeMillis()
        _ephemeralExpression.value = EphemeralExpressionPolicy.create(
            emotion = emotion,
            valence = mood.valence,
            baseline = mood.baseline,
            now = now,
        )
    }

    /**
     * Completed LLM turn: ephemeral expression + optional valence shift.
     * [userTone] is the LLM's judgement of the user's utterance (JSON `user_tone`).
     */
    fun applyLlmTurnEmotion(emotion: RobotEmotion?, userTone: UserInteractionTone? = null) {
        lastInteractionTime = System.currentTimeMillis()
        if (userTone == UserInteractionTone.APOLOGY) {
            onTrigger(MoodTrigger.UserApology)
        }
        val mood = _currentMood.value
        val (session, signals) = TurnMoodEvaluator.evaluateLlmTurn(
            emotion = emotion,
            userTone = userTone,
            session = conversationSession,
            config = turnMoodConfig,
        )
        conversationSession = session
        if (signals.promptHints.isNotEmpty()) {
            turnPromptHints = signals.promptHints
        }
        _ephemeralExpression.value = EphemeralExpressionPolicy.create(
            emotion = emotion,
            valence = mood.valence,
            baseline = mood.baseline,
            intensityScale = signals.ephemeralIntensityScale,
        )
        if (emotion != null && signals.llmEmotionValenceTier != LlmEmotionValenceTier.NONE) {
            onTrigger(MoodTrigger.LlmEmotion(emotion, signals.llmEmotionValenceTier))
        }
    }

    fun clearExpiredEphemeral(now: Long = System.currentTimeMillis()) {
        val current = _ephemeralExpression.value ?: return
        if (!current.isActive(now)) {
            _ephemeralExpression.value = null
        }
    }

    fun getIdleMinutes(): Long =
        (System.currentTimeMillis() - lastInteractionTime) / 60_000L

    fun getMinutesSinceLastVoiceTurn(): Long =
        (System.currentTimeMillis() - lastVoiceTurnTime) / 60_000L

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
