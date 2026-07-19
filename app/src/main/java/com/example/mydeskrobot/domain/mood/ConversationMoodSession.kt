package com.example.mydeskrobot.domain.mood

/**
 * Volatile per hotword session mood signals (burst, repetition, praise cap).
 * Reset when mic session ends.
 */
data class ConversationMoodSession(
    val lastVoiceTurnAtMs: Long? = null,
    val burstWindowStartMs: Long? = null,
    val turnsInBurstWindow: Int = 0,
    val recentTurnSignatures: List<String> = emptyList(),
    val lastToolSuccessAtMs: Long? = null,
    val positiveBoostsInWindow: Int = 0,
    val positiveBoostWindowStartMs: Long? = null,
) {
    fun onUserTurn(phrase: String, now: Long, config: TurnMoodConfig): ConversationMoodSession {
        val signature = normalizePhrase(phrase)
        val recent = (recentTurnSignatures + signature).takeLast(config.maxRecentSignatures)
        val burstStart = burstWindowStartMs ?: now
        val burstElapsed = now - burstStart
        val inSameBurstWindow = burstElapsed <= config.burstWindowMs
        val newBurstStart = if (inSameBurstWindow) burstStart else now
        val newBurstCount = if (inSameBurstWindow) turnsInBurstWindow + 1 else 1

        val praiseStart = positiveBoostWindowStartMs ?: now
        val praiseElapsed = now - praiseStart
        val inSamePraiseWindow = praiseElapsed <= config.positiveBoostWindowMs

        return copy(
            lastVoiceTurnAtMs = now,
            burstWindowStartMs = newBurstStart,
            turnsInBurstWindow = newBurstCount,
            recentTurnSignatures = recent,
            positiveBoostWindowStartMs = if (inSamePraiseWindow) praiseStart else now,
            positiveBoostsInWindow = if (inSamePraiseWindow) positiveBoostsInWindow else 0,
        )
    }

    fun onToolSuccess(now: Long): ConversationMoodSession =
        copy(lastToolSuccessAtMs = now)

    fun withPositiveBoostRecorded(now: Long, config: TurnMoodConfig): ConversationMoodSession {
        val praiseStart = positiveBoostWindowStartMs ?: now
        val praiseElapsed = now - praiseStart
        val inSamePraiseWindow = praiseElapsed <= config.positiveBoostWindowMs
        return copy(
            positiveBoostWindowStartMs = if (inSamePraiseWindow) praiseStart else now,
            positiveBoostsInWindow = if (inSamePraiseWindow) {
                positiveBoostsInWindow + 1
            } else {
                1
            },
        )
    }

    fun repetitionCount(signature: String): Int =
        recentTurnSignatures.count { it == signature }

    companion object {
        fun reset(): ConversationMoodSession = ConversationMoodSession()

        fun normalizePhrase(phrase: String): String =
            phrase.lowercase().trim().replace(Regex("\\s+"), " ")
    }
}

data class TurnMoodConfig(
    val burstTurnCountThreshold: Int = MoodConfig.DEFAULT_BURST_TURN_COUNT,
    val burstWindowMs: Long = MoodConfig.DEFAULT_BURST_WINDOW_MINUTES * 60_000L,
    val repeatedPhraseThreshold: Int = MoodConfig.DEFAULT_REPEATED_PHRASE_THRESHOLD,
    val shortPhraseWordLimit: Int = MoodConfig.DEFAULT_SHORT_PHRASE_WORD_LIMIT,
    val maxRecentSignatures: Int = 8,
    val positiveBoostCapPerWindow: Int = MoodConfig.DEFAULT_POSITIVE_BOOST_CAP,
    val positiveBoostWindowMs: Long = 60 * 60_000L,
)
