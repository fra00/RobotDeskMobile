package com.example.mydeskrobot.data.reflection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.reflection.WeeklyStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.weeklyStatsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "weekly_stats"
)

/**
 * Persists weekly statistics for self-reflection.
 * Automatically resets when the week changes.
 */
class WeeklyStatsRepository(
    private val context: Context,
) {
    val stats: Flow<WeeklyStats> =
        context.weeklyStatsDataStore.data.map { prefs ->
            val storedWeekKey = prefs[KEY_WEEK_KEY] ?: 0
            val currentWeekKey = WeeklyStats.currentWeekKey()

            if (storedWeekKey != currentWeekKey) {
                WeeklyStats.forCurrentWeek()
            } else {
                WeeklyStats(
                    weekKey = storedWeekKey,
                    totalInteractions = prefs[KEY_TOTAL_INTERACTIONS] ?: 0,
                    totalProactiveSpeaks = prefs[KEY_TOTAL_PROACTIVE] ?: 0,
                    positiveResponses = prefs[KEY_POSITIVE_RESPONSES] ?: 0,
                    ignoredSuggestions = prefs[KEY_IGNORED_SUGGESTIONS] ?: 0,
                    usefulTopics = parseTopicMap(prefs[KEY_USEFUL_TOPICS]),
                    ignoredTopics = parseTopicMap(prefs[KEY_IGNORED_TOPICS]),
                    lastReflectionMillis = prefs[KEY_LAST_REFLECTION],
                )
            }
        }

    suspend fun load(): WeeklyStats = stats.first()

    suspend fun save(stats: WeeklyStats) {
        context.weeklyStatsDataStore.edit { prefs ->
            prefs[KEY_WEEK_KEY] = stats.weekKey
            prefs[KEY_TOTAL_INTERACTIONS] = stats.totalInteractions
            prefs[KEY_TOTAL_PROACTIVE] = stats.totalProactiveSpeaks
            prefs[KEY_POSITIVE_RESPONSES] = stats.positiveResponses
            prefs[KEY_IGNORED_SUGGESTIONS] = stats.ignoredSuggestions
            prefs[KEY_USEFUL_TOPICS] = serializeTopicMap(stats.usefulTopics)
            prefs[KEY_IGNORED_TOPICS] = serializeTopicMap(stats.ignoredTopics)
            if (stats.lastReflectionMillis != null) {
                prefs[KEY_LAST_REFLECTION] = stats.lastReflectionMillis
            } else {
                prefs.remove(KEY_LAST_REFLECTION)
            }
        }
    }

    suspend fun recordInteraction() {
        update { it.withInteraction() }
    }

    suspend fun recordProactiveSpeak() {
        update { it.withProactiveSpeak() }
    }

    suspend fun recordPositiveResponse(topic: String?) {
        update { it.withPositiveResponse(topic) }
    }

    suspend fun recordIgnoredSuggestion(topic: String?) {
        update { it.withIgnoredSuggestion(topic) }
    }

    suspend fun markReflectionDone() {
        update { it.withReflectionDone() }
    }

    /**
     * Check if a new week started. Returns the previous week's stats if so.
     */
    suspend fun checkWeekRollover(): WeeklyStats? {
        val current = load()
        val currentWeekKey = WeeklyStats.currentWeekKey()
        return if (current.weekKey != 0 && current.weekKey != currentWeekKey) {
            val previousStats = current
            save(WeeklyStats.forCurrentWeek())
            previousStats
        } else {
            null
        }
    }

    /**
     * Check if reflection is due (once per week, after at least some activity).
     */
    suspend fun isReflectionDue(): Boolean {
        val stats = load()
        if (stats.lastReflectionMillis != null) return false
        if (stats.totalProactiveSpeaks < 3) return false
        return true
    }

    private suspend fun update(transform: (WeeklyStats) -> WeeklyStats) {
        val current = load()
        val currentWeekKey = WeeklyStats.currentWeekKey()
        val base = if (current.weekKey != currentWeekKey) {
            WeeklyStats.forCurrentWeek()
        } else {
            current
        }
        save(transform(base))
    }

    private fun parseTopicMap(serialized: String?): Map<String, Int> {
        if (serialized.isNullOrBlank()) return emptyMap()
        return serialized.split(ENTRY_SEPARATOR)
            .mapNotNull { entry ->
                val parts = entry.split(KV_SEPARATOR)
                if (parts.size == 2) {
                    val topic = parts[0]
                    val count = parts[1].toIntOrNull() ?: 0
                    topic to count
                } else null
            }
            .toMap()
    }

    private fun serializeTopicMap(map: Map<String, Int>): String =
        map.entries
            .take(MAX_TOPICS)
            .joinToString(ENTRY_SEPARATOR) { "${it.key}$KV_SEPARATOR${it.value}" }

    companion object {
        private val KEY_WEEK_KEY = intPreferencesKey("ws_week_key")
        private val KEY_TOTAL_INTERACTIONS = intPreferencesKey("ws_total_interactions")
        private val KEY_TOTAL_PROACTIVE = intPreferencesKey("ws_total_proactive")
        private val KEY_POSITIVE_RESPONSES = intPreferencesKey("ws_positive_responses")
        private val KEY_IGNORED_SUGGESTIONS = intPreferencesKey("ws_ignored_suggestions")
        private val KEY_USEFUL_TOPICS = stringPreferencesKey("ws_useful_topics")
        private val KEY_IGNORED_TOPICS = stringPreferencesKey("ws_ignored_topics")
        private val KEY_LAST_REFLECTION = longPreferencesKey("ws_last_reflection")

        private const val ENTRY_SEPARATOR = "|||"
        private const val KV_SEPARATOR = ":::"
        private const val MAX_TOPICS = 20
    }
}
