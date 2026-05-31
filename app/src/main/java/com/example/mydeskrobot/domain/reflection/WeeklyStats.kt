package com.example.mydeskrobot.domain.reflection

/**
 * Statistics for the robot's behavior over a week.
 * Used for self-reflection to learn what works and what doesn't.
 */
data class WeeklyStats(
    /** Week identifier (YYYYWW format). */
    val weekKey: Int = 0,
    /** Total user interactions this week. */
    val totalInteractions: Int = 0,
    /** Total proactive speaks (heartbeat interventions). */
    val totalProactiveSpeaks: Int = 0,
    /** Proactive speaks that led to user engagement (follow-up interaction). */
    val positiveResponses: Int = 0,
    /** Proactive speaks that were ignored (no follow-up within 5 min). */
    val ignoredSuggestions: Int = 0,
    /** Topics that led to positive engagement (topic -> count). */
    val usefulTopics: Map<String, Int> = emptyMap(),
    /** Topics that were ignored or got negative response (topic -> count). */
    val ignoredTopics: Map<String, Int> = emptyMap(),
    /** Timestamp of the last reflection run. */
    val lastReflectionMillis: Long? = null,
) {
    /**
     * Success rate of proactive speaks (0.0-1.0).
     */
    fun successRate(): Float {
        val total = positiveResponses + ignoredSuggestions
        return if (total > 0) positiveResponses.toFloat() / total else 0f
    }

    /**
     * Get top N useful topics.
     */
    fun topUsefulTopics(n: Int = 5): List<String> =
        usefulTopics.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }

    /**
     * Get top N ignored topics.
     */
    fun topIgnoredTopics(n: Int = 5): List<String> =
        ignoredTopics.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }

    fun withInteraction(): WeeklyStats =
        copy(totalInteractions = totalInteractions + 1)

    fun withProactiveSpeak(): WeeklyStats =
        copy(totalProactiveSpeaks = totalProactiveSpeaks + 1)

    fun withPositiveResponse(topic: String?): WeeklyStats {
        val updated = copy(positiveResponses = positiveResponses + 1)
        return if (topic != null) {
            val newUseful = usefulTopics.toMutableMap()
            newUseful[topic] = (newUseful[topic] ?: 0) + 1
            updated.copy(usefulTopics = newUseful)
        } else updated
    }

    fun withIgnoredSuggestion(topic: String?): WeeklyStats {
        val updated = copy(ignoredSuggestions = ignoredSuggestions + 1)
        return if (topic != null) {
            val newIgnored = ignoredTopics.toMutableMap()
            newIgnored[topic] = (newIgnored[topic] ?: 0) + 1
            updated.copy(ignoredTopics = newIgnored)
        } else updated
    }

    fun withReflectionDone(timestamp: Long = System.currentTimeMillis()): WeeklyStats =
        copy(lastReflectionMillis = timestamp)

    companion object {
        /** Get current week key (YYYYWW). */
        fun currentWeekKey(): Int {
            val cal = java.util.Calendar.getInstance()
            val year = cal.get(java.util.Calendar.YEAR)
            val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
            return year * 100 + week
        }

        /** Create fresh stats for current week. */
        fun forCurrentWeek(): WeeklyStats = WeeklyStats(weekKey = currentWeekKey())
    }
}
