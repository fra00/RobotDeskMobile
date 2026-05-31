package com.example.mydeskrobot.domain.memory

/**
 * Volatile buffer of "what happened today".
 * Resets at midnight or on first "buongiorno" of the day.
 * Used to prevent repetitions and track daily patterns.
 */
data class WorkingMemory(
    /** Number of user interactions today. */
    val todayInteractions: Int = 0,
    /** Inferred mood of the user from recent interactions (e.g., "stressed", "relaxed"). */
    val lastUserMood: String? = null,
    /** Topics discussed today (to avoid repetition). */
    val topicsDiscussedToday: List<String> = emptyList(),
    /** Number of proactive (heartbeat) speaks today. */
    val proactiveSpeaksToday: Int = 0,
    /** Number of proactive suggestions that were ignored/rejected. */
    val ignoredSuggestionsToday: Int = 0,
    /** Timestamp of the last proactive speak (to enforce cooldown). */
    val lastProactiveSpeakMillis: Long? = null,
    /** The date this memory belongs to (YYYYMMDD format for easy comparison). */
    val dateKey: Int = 0,
) {
    /**
     * Check if a topic was already discussed today.
     */
    fun hasDiscussedTopic(topic: String): Boolean =
        topicsDiscussedToday.any { it.equals(topic, ignoreCase = true) }

    /**
     * Add a topic to the discussed list.
     */
    fun withTopic(topic: String): WorkingMemory {
        if (hasDiscussedTopic(topic)) return this
        return copy(topicsDiscussedToday = topicsDiscussedToday + topic)
    }

    /**
     * Record a user interaction.
     */
    fun withInteraction(): WorkingMemory =
        copy(todayInteractions = todayInteractions + 1)

    /**
     * Record a proactive speak.
     */
    fun withProactiveSpeak(timestamp: Long = System.currentTimeMillis()): WorkingMemory =
        copy(
            proactiveSpeaksToday = proactiveSpeaksToday + 1,
            lastProactiveSpeakMillis = timestamp,
        )

    /**
     * Record an ignored suggestion.
     */
    fun withIgnoredSuggestion(): WorkingMemory =
        copy(ignoredSuggestionsToday = ignoredSuggestionsToday + 1)

    /**
     * Update inferred user mood.
     */
    fun withUserMood(mood: String?): WorkingMemory =
        copy(lastUserMood = mood)

    /**
     * Minutes since the last proactive speak.
     */
    fun minutesSinceLastProactiveSpeak(now: Long = System.currentTimeMillis()): Long? {
        val last = lastProactiveSpeakMillis ?: return null
        return (now - last) / 60_000L
    }

    companion object {
        /** Maximum topics to track per day. */
        const val MAX_TOPICS = 20

        /** Create a fresh working memory for today. */
        fun forToday(): WorkingMemory = WorkingMemory(dateKey = todayKey())

        /** Get today's date key (YYYYMMDD). */
        fun todayKey(): Int {
            val cal = java.util.Calendar.getInstance()
            val year = cal.get(java.util.Calendar.YEAR)
            val month = cal.get(java.util.Calendar.MONTH) + 1
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            return year * 10000 + month * 100 + day
        }
    }
}
