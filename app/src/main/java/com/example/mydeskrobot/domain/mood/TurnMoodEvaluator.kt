package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Scores user turns and LLM emotions for wellbeing deltas and prompt hints.
 */
object TurnMoodEvaluator {

    private val TASK_HINT_KEYWORDS = setOf(
        "ricorda", "promemoria", "sveglia", "allarme", "lista", "meteo",
        "apri", "chiama", "whatsapp", "spotify", "cerca", "memorizza",
        "salva", "elimina", "cancella", "tool", "foto", "camera",
    )

    fun evaluateUserTurn(
        phrase: String,
        session: ConversationMoodSession,
        config: TurnMoodConfig = TurnMoodConfig(),
        valenceConfig: MoodValenceConfig = MoodValenceConfig(),
        now: Long = System.currentTimeMillis(),
    ): Pair<ConversationMoodSession, TurnMoodSignals> {
        val trimmed = phrase.trim()
        if (trimmed.isBlank()) {
            return session to TurnMoodSignals()
        }

        val updated = session.onUserTurn(trimmed, now, config)
        val signature = ConversationMoodSession.normalizePhrase(trimmed)
        val wordCount = trimmed.split(Regex("\\s+")).size
        val looksLikeTask = TASK_HINT_KEYWORDS.any { trimmed.lowercase().contains(it) }

        val triggers = mutableListOf<MoodTrigger>()
        val hints = mutableListOf<String>()

        val presenceDelta = if (wordCount <= config.shortPhraseWordLimit && !looksLikeTask) {
            valenceConfig.shortPhrasePresence
        } else {
            valenceConfig.voiceTurnPresence
        }
        triggers += MoodTrigger.VoiceTurnPresence(presenceDelta)

        if (updated.turnsInBurstWindow >= config.burstTurnCountThreshold) {
            triggers += MoodTrigger.ValenceDelta(
                delta = valenceConfig.burstFatigue,
                event = "fatigue_burst",
                reason = MoodReason.CONVERSATION_FATIGUE,
            )
            hints += "Conversazione intensa: preferisci risposte brevi; emotion neutral o bored leggero."
        }

        if (updated.repetitionCount(signature) >= config.repeatedPhraseThreshold) {
            triggers += MoodTrigger.ValenceDelta(
                delta = valenceConfig.repeatedPhraseFatigue,
                event = "frase_ripetuta",
                reason = MoodReason.CONVERSATION_FATIGUE,
            )
            hints += "Richiesta ripetuta: se possibile varia tono o chiedi chiarimento."
        }

        return updated to TurnMoodSignals(
            triggers = triggers,
            promptHints = hints,
        )
    }

    /**
     * Completed LLM turn. [userTone] is the LLM's own judgement of the user's utterance
     * (JSON `user_tone`) — genuine praise within the hourly cap promotes happy/loving to FULL.
     * Returns the updated session (praise cap bookkeeping) plus the turn signals.
     */
    fun evaluateLlmTurn(
        emotion: RobotEmotion?,
        userTone: UserInteractionTone?,
        session: ConversationMoodSession,
        config: TurnMoodConfig = TurnMoodConfig(),
        now: Long = System.currentTimeMillis(),
    ): Pair<ConversationMoodSession, TurnMoodSignals> {
        if (emotion == null) return session to TurnMoodSignals()

        var updated = session
        val extraHints = mutableListOf<String>()
        var praiseAccepted = false
        if (userTone == UserInteractionTone.POSITIVE) {
            if (updated.positiveBoostsInWindow < config.positiveBoostCapPerWindow) {
                praiseAccepted = true
                updated = updated.withPositiveBoostRecorded(now, config)
            } else {
                extraHints += "Elogi frequenti: tono caldo ma emotion preferibilmente neutral."
            }
        }

        // Tool success rewards valence via TaskCompletedUseful only — a happy ack after a tool
        // stays ROUTINE to avoid double counting.
        val tier = when {
            emotion == RobotEmotion.HAPPY || emotion == RobotEmotion.LOVING -> {
                if (praiseAccepted) LlmEmotionValenceTier.FULL else LlmEmotionValenceTier.ROUTINE
            }
            emotion == RobotEmotion.NEUTRAL || emotion == RobotEmotion.THINKING -> LlmEmotionValenceTier.NONE
            else -> LlmEmotionValenceTier.FULL
        }

        val intensityScale = when {
            emotion == RobotEmotion.HAPPY || emotion == RobotEmotion.LOVING -> {
                if (tier == LlmEmotionValenceTier.ROUTINE) 0.5f else null
            }
            else -> null
        }

        val toolRecent = updated.lastToolSuccessAtMs?.let { now - it <= 120_000L } == true
        val hints = buildList {
            addAll(extraHints)
            if (tier == LlmEmotionValenceTier.ROUTINE) {
                add("Turno routinario: emotion neutral o thinking salvo tono emotivo reale.")
            }
            if (toolRecent && emotion == RobotEmotion.HAPPY) {
                add("Ack post-tool: preferisci neutral, non happy di default.")
            }
        }

        return updated to TurnMoodSignals(
            promptHints = hints,
            llmEmotionValenceTier = tier,
            ephemeralIntensityScale = intensityScale,
        )
    }
}
