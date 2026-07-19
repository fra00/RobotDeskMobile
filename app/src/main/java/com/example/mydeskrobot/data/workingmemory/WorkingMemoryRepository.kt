package com.example.mydeskrobot.data.workingmemory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
                    topicsDiscussedToday = prefs[KEY_TOPICS]?.split(TOPIC_SEPARATOR)
                        ?.filter { it.isNotBlank() } ?: emptyList(),
                    proactiveSpeaksToday = prefs[KEY_PROACTIVE_SPEAKS] ?: 0,
                    ignoredSuggestionsToday = prefs[KEY_IGNORED_SUGGESTIONS] ?: 0,
                    lastProactiveSpeakMillis = prefs[KEY_LAST_PROACTIVE_SPEAK],
                    lastUserTurnMillis = prefs[KEY_LAST_USER_TURN],
                    deviationAskedSlotKeysToday = prefs[KEY_DEVIATION_ASKED]?.split(SLOT_SEPARATOR)
                        ?.filter { it.isNotBlank() }
                        ?.toSet() ?: emptySet(),
                    deviationSuppressedSlotKeysToday = prefs[KEY_DEVIATION_SUPPRESSED]?.split(SLOT_SEPARATOR)
                        ?.filter { it.isNotBlank() }
                        ?.toSet() ?: emptySet(),
                    firstHotwordOnTodayMs = prefs[KEY_FIRST_HOTWORD_ON],
                    wellnessCheckDoneToday = prefs[KEY_WELLNESS_CHECK_DONE] ?: false,
                    wellnessVisualDoneToday = prefs[KEY_WELLNESS_VISUAL_DONE] ?: false,
                    dateKey = storedDateKey,
                )
            }
        }

    suspend fun load(): WorkingMemory = memory.first()

    suspend fun save(memory: WorkingMemory) {
        context.workingMemoryDataStore.edit { prefs ->
            prefs[KEY_DATE_KEY] = memory.dateKey
            prefs[KEY_TODAY_INTERACTIONS] = memory.todayInteractions
            prefs.remove(KEY_LAST_USER_MOOD)
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
            if (memory.lastUserTurnMillis != null) {
                prefs[KEY_LAST_USER_TURN] = memory.lastUserTurnMillis
            } else {
                prefs.remove(KEY_LAST_USER_TURN)
            }
            prefs[KEY_DEVIATION_ASKED] = memory.deviationAskedSlotKeysToday
                .joinToString(SLOT_SEPARATOR)
            prefs[KEY_DEVIATION_SUPPRESSED] = memory.deviationSuppressedSlotKeysToday
                .joinToString(SLOT_SEPARATOR)
            if (memory.firstHotwordOnTodayMs != null) {
                prefs[KEY_FIRST_HOTWORD_ON] = memory.firstHotwordOnTodayMs
            } else {
                prefs.remove(KEY_FIRST_HOTWORD_ON)
            }
            prefs[KEY_WELLNESS_CHECK_DONE] = memory.wellnessCheckDoneToday
            prefs[KEY_WELLNESS_VISUAL_DONE] = memory.wellnessVisualDoneToday
        }
    }

    suspend fun recordUserTurn(timestamp: Long = System.currentTimeMillis()) {
        update { it.withUserTurn(timestamp) }
    }

    suspend fun recordDeviationAsked(slotKey: String) {
        update { it.withDeviationAsked(slotKey) }
    }

    suspend fun recordDeviationSuppressed(slotKey: String) {
        update { it.withDeviationSuppressed(slotKey) }
    }

    suspend fun recordFirstHotwordOn(timestamp: Long = System.currentTimeMillis()) {
        update { it.withFirstHotwordOn(timestamp) }
    }

    suspend fun recordWellnessCheckDone() {
        update { it.withWellnessCheckDone() }
    }

    suspend fun recordWellnessVisualDone() {
        update { it.withWellnessVisualDone() }
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
        private val KEY_LAST_USER_TURN = longPreferencesKey("wm_last_user_turn")
        private val KEY_DEVIATION_ASKED = stringPreferencesKey("wm_deviation_asked")
        private val KEY_DEVIATION_SUPPRESSED = stringPreferencesKey("wm_deviation_suppressed")
        private val KEY_FIRST_HOTWORD_ON = longPreferencesKey("wm_first_hotword_on")
        private val KEY_WELLNESS_CHECK_DONE = booleanPreferencesKey("wm_wellness_check_done")
        private val KEY_WELLNESS_VISUAL_DONE = booleanPreferencesKey("wm_wellness_visual_done")

        private const val TOPIC_SEPARATOR = "|||"
        private const val SLOT_SEPARATOR = "|||"
    }
}
