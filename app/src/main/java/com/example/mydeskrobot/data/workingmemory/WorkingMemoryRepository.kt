package com.example.mydeskrobot.data.workingmemory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.memory.WorkingMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.workingMemoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "working_memory"
)

/**
 * Persists the robot's working memory (daily volatile buffer).
 * Automatically resets when the date changes.
 */
class WorkingMemoryRepository(
    private val context: Context,
) {
    val memory: Flow<WorkingMemory> =
        context.workingMemoryDataStore.data.map { prefs ->
            val storedDateKey = prefs[KEY_DATE_KEY] ?: 0
            val todayKey = WorkingMemory.todayKey()

            if (storedDateKey != todayKey) {
                WorkingMemory.forToday()
            } else {
                WorkingMemory(
                    todayInteractions = prefs[KEY_TODAY_INTERACTIONS] ?: 0,
                    lastUserMood = prefs[KEY_LAST_USER_MOOD],
                    topicsDiscussedToday = prefs[KEY_TOPICS]?.split(TOPIC_SEPARATOR)
                        ?.filter { it.isNotBlank() } ?: emptyList(),
                    proactiveSpeaksToday = prefs[KEY_PROACTIVE_SPEAKS] ?: 0,
                    ignoredSuggestionsToday = prefs[KEY_IGNORED_SUGGESTIONS] ?: 0,
                    lastProactiveSpeakMillis = prefs[KEY_LAST_PROACTIVE_SPEAK],
                    dateKey = storedDateKey,
                )
            }
        }

    suspend fun load(): WorkingMemory = memory.first()

    suspend fun save(memory: WorkingMemory) {
        context.workingMemoryDataStore.edit { prefs ->
            prefs[KEY_DATE_KEY] = memory.dateKey
            prefs[KEY_TODAY_INTERACTIONS] = memory.todayInteractions
            if (memory.lastUserMood != null) {
                prefs[KEY_LAST_USER_MOOD] = memory.lastUserMood
            } else {
                prefs.remove(KEY_LAST_USER_MOOD)
            }
            prefs[KEY_TOPICS] = memory.topicsDiscussedToday
                .take(WorkingMemory.MAX_TOPICS)
                .joinToString(TOPIC_SEPARATOR)
            prefs[KEY_PROACTIVE_SPEAKS] = memory.proactiveSpeaksToday
            prefs[KEY_IGNORED_SUGGESTIONS] = memory.ignoredSuggestionsToday
            if (memory.lastProactiveSpeakMillis != null) {
                prefs[KEY_LAST_PROACTIVE_SPEAK] = memory.lastProactiveSpeakMillis
            } else {
                prefs.remove(KEY_LAST_PROACTIVE_SPEAK)
            }
        }
    }

    suspend fun recordInteraction() {
        update { it.withInteraction() }
    }

    suspend fun recordTopic(topic: String) {
        update { it.withTopic(topic) }
    }

    suspend fun recordProactiveSpeak() {
        update { it.withProactiveSpeak() }
    }

    suspend fun recordIgnoredSuggestion() {
        update { it.withIgnoredSuggestion() }
    }

    suspend fun updateUserMood(mood: String?) {
        update { it.withUserMood(mood) }
    }

    suspend fun reset() {
        context.workingMemoryDataStore.edit { prefs ->
            prefs.clear()
        }
        save(WorkingMemory.forToday())
    }

    /**
     * Check if it's a new day and reset if needed.
     * Returns true if reset occurred.
     */
    suspend fun checkAndResetIfNewDay(): Boolean {
        val current = load()
        val todayKey = WorkingMemory.todayKey()
        if (current.dateKey != todayKey) {
            reset()
            return true
        }
        return false
    }

    private suspend fun update(transform: (WorkingMemory) -> WorkingMemory) {
        val current = load()
        val todayKey = WorkingMemory.todayKey()
        val base = if (current.dateKey != todayKey) {
            WorkingMemory.forToday()
        } else {
            current
        }
        save(transform(base))
    }

    companion object {
        private val KEY_DATE_KEY = intPreferencesKey("wm_date_key")
        private val KEY_TODAY_INTERACTIONS = intPreferencesKey("wm_today_interactions")
        private val KEY_LAST_USER_MOOD = stringPreferencesKey("wm_last_user_mood")
        private val KEY_TOPICS = stringPreferencesKey("wm_topics")
        private val KEY_PROACTIVE_SPEAKS = intPreferencesKey("wm_proactive_speaks")
        private val KEY_IGNORED_SUGGESTIONS = intPreferencesKey("wm_ignored_suggestions")
        private val KEY_LAST_PROACTIVE_SPEAK = longPreferencesKey("wm_last_proactive_speak")

        private const val TOPIC_SEPARATOR = "|||"
    }
}
