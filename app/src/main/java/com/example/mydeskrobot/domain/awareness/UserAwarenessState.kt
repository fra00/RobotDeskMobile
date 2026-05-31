package com.example.mydeskrobot.domain.awareness

/**
 * Theory of Mind: what the robot believes about the user's mental state.
 * Tracks not just facts, but what the user probably knows, feels, and expects.
 */
data class UserAwarenessState(
    /** Inferred user mood based on interaction patterns. */
    val inferredMood: UserMood = UserMood.UNKNOWN,
    /** Topics the user probably knows about (already discussed/seen today). */
    val userProbablyKnows: Set<String> = emptySet(),
    /** Topics the user probably doesn't know about (new info available). */
    val userProbablyDoesNotKnow: Set<String> = emptySet(),
    /** Recent interaction patterns for mood inference. */
    val recentResponseLengths: List<Int> = emptyList(),
    /** Number of short/curt responses today (may indicate stress/busyness). */
    val shortResponsesCount: Int = 0,
    /** Number of enthusiastic/long responses today (may indicate engagement). */
    val engagedResponsesCount: Int = 0,
    /** Last time user mentioned being busy/stressed. */
    val lastBusyMentionMillis: Long? = null,
    /** Last time user was enthusiastic/happy in interaction. */
    val lastPositiveInteractionMillis: Long? = null,
    /** Today's date key for daily reset. */
    val dateKey: Int = 0,
) {
    /**
     * Check if user probably already knows about a topic.
     */
    fun userKnowsAbout(topic: String): Boolean =
        userProbablyKnows.any { it.equals(topic, ignoreCase = true) }

    /**
     * Mark that user now knows about a topic.
     */
    fun withUserKnowsAbout(topic: String): UserAwarenessState =
        copy(
            userProbablyKnows = userProbablyKnows + topic.lowercase(),
            userProbablyDoesNotKnow = userProbablyDoesNotKnow - topic.lowercase(),
        )

    /**
     * Mark that there's new info the user doesn't know about.
     */
    fun withNewInfoAvailable(topic: String): UserAwarenessState =
        copy(userProbablyDoesNotKnow = userProbablyDoesNotKnow + topic.lowercase())

    /**
     * Record a user response length for mood inference.
     */
    fun withUserResponse(wordCount: Int): UserAwarenessState {
        val isShort = wordCount <= SHORT_RESPONSE_THRESHOLD
        val isEngaged = wordCount >= ENGAGED_RESPONSE_THRESHOLD
        
        val newLengths = (recentResponseLengths + wordCount).takeLast(MAX_RECENT_RESPONSES)
        
        return copy(
            recentResponseLengths = newLengths,
            shortResponsesCount = if (isShort) shortResponsesCount + 1 else shortResponsesCount,
            engagedResponsesCount = if (isEngaged) engagedResponsesCount + 1 else engagedResponsesCount,
            inferredMood = inferMoodFromPatterns(newLengths, shortResponsesCount, engagedResponsesCount),
        )
    }

    /**
     * Record that user mentioned being busy or stressed.
     */
    fun withBusyMention(timestamp: Long = System.currentTimeMillis()): UserAwarenessState =
        copy(
            lastBusyMentionMillis = timestamp,
            inferredMood = UserMood.BUSY,
        )

    /**
     * Record a positive/enthusiastic interaction.
     */
    fun withPositiveInteraction(timestamp: Long = System.currentTimeMillis()): UserAwarenessState =
        copy(
            lastPositiveInteractionMillis = timestamp,
            inferredMood = UserMood.RELAXED,
        )

    private fun inferMoodFromPatterns(
        lengths: List<Int>,
        shortCount: Int,
        engagedCount: Int,
    ): UserMood {
        if (lengths.isEmpty()) return UserMood.UNKNOWN
        
        val avgLength = lengths.average()
        val recentShortRatio = shortCount.toFloat() / maxOf(1, shortCount + engagedCount)
        
        return when {
            recentShortRatio > 0.7f -> UserMood.BUSY
            avgLength > ENGAGED_RESPONSE_THRESHOLD -> UserMood.RELAXED
            avgLength < SHORT_RESPONSE_THRESHOLD -> UserMood.BUSY
            else -> UserMood.NEUTRAL
        }
    }

    companion object {
        private const val SHORT_RESPONSE_THRESHOLD = 5
        private const val ENGAGED_RESPONSE_THRESHOLD = 20
        private const val MAX_RECENT_RESPONSES = 10
        private const val MAX_KNOWN_TOPICS = 20

        fun todayKey(): Int {
            val cal = java.util.Calendar.getInstance()
            val year = cal.get(java.util.Calendar.YEAR)
            val month = cal.get(java.util.Calendar.MONTH) + 1
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            return year * 10000 + month * 100 + day
        }

        fun forToday(): UserAwarenessState = UserAwarenessState(dateKey = todayKey())
    }
}

/**
 * Inferred mood of the user based on interaction patterns.
 */
enum class UserMood {
    /** Not enough data to infer. */
    UNKNOWN,
    /** User seems neutral/normal. */
    NEUTRAL,
    /** User seems busy/stressed (short responses, mentions of being busy). */
    BUSY,
    /** User seems relaxed/engaged (longer responses, positive tone). */
    RELAXED,
    /** User seems frustrated (negative keywords, abrupt endings). */
    FRUSTRATED,
}
