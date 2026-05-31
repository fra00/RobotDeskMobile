package com.example.mydeskrobot.data.awareness

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.awareness.UserAwarenessState
import com.example.mydeskrobot.domain.awareness.UserMood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userAwarenessDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_awareness"
)

/**
 * Persists user awareness state (Theory of Mind).
 * Resets daily since user state changes.
 */
class UserAwarenessRepository(
    private val context: Context,
) {
    val state: Flow<UserAwarenessState> =
        context.userAwarenessDataStore.data.map { prefs ->
            val storedDateKey = prefs[KEY_DATE_KEY] ?: 0
            val todayKey = UserAwarenessState.todayKey()

            if (storedDateKey != todayKey) {
                UserAwarenessState.forToday()
            } else {
                UserAwarenessState(
                    inferredMood = prefs[KEY_MOOD]?.let {
                        runCatching { UserMood.valueOf(it) }.getOrDefault(UserMood.UNKNOWN)
                    } ?: UserMood.UNKNOWN,
                    userProbablyKnows = prefs[KEY_USER_KNOWS]
                        ?.split(SEPARATOR)
                        ?.filter { it.isNotBlank() }
                        ?.toSet() ?: emptySet(),
                    userProbablyDoesNotKnow = prefs[KEY_USER_DOESNT_KNOW]
                        ?.split(SEPARATOR)
                        ?.filter { it.isNotBlank() }
                        ?.toSet() ?: emptySet(),
                    shortResponsesCount = prefs[KEY_SHORT_RESPONSES] ?: 0,
                    engagedResponsesCount = prefs[KEY_ENGAGED_RESPONSES] ?: 0,
                    lastBusyMentionMillis = prefs[KEY_LAST_BUSY],
                    lastPositiveInteractionMillis = prefs[KEY_LAST_POSITIVE],
                    dateKey = storedDateKey,
                )
            }
        }

    suspend fun load(): UserAwarenessState = state.first()

    suspend fun save(state: UserAwarenessState) {
        context.userAwarenessDataStore.edit { prefs ->
            prefs[KEY_DATE_KEY] = state.dateKey
            prefs[KEY_MOOD] = state.inferredMood.name
            prefs[KEY_USER_KNOWS] = state.userProbablyKnows.take(MAX_TOPICS).joinToString(SEPARATOR)
            prefs[KEY_USER_DOESNT_KNOW] = state.userProbablyDoesNotKnow.take(MAX_TOPICS).joinToString(SEPARATOR)
            prefs[KEY_SHORT_RESPONSES] = state.shortResponsesCount
            prefs[KEY_ENGAGED_RESPONSES] = state.engagedResponsesCount
            if (state.lastBusyMentionMillis != null) {
                prefs[KEY_LAST_BUSY] = state.lastBusyMentionMillis
            } else {
                prefs.remove(KEY_LAST_BUSY)
            }
            if (state.lastPositiveInteractionMillis != null) {
                prefs[KEY_LAST_POSITIVE] = state.lastPositiveInteractionMillis
            } else {
                prefs.remove(KEY_LAST_POSITIVE)
            }
        }
    }

    suspend fun update(transform: (UserAwarenessState) -> UserAwarenessState) {
        val current = load()
        val todayKey = UserAwarenessState.todayKey()
        val base = if (current.dateKey != todayKey) {
            UserAwarenessState.forToday()
        } else {
            current
        }
        save(transform(base))
    }

    suspend fun checkAndResetIfNewDay(): Boolean {
        val current = load()
        val todayKey = UserAwarenessState.todayKey()
        if (current.dateKey != todayKey) {
            save(UserAwarenessState.forToday())
            return true
        }
        return false
    }

    companion object {
        private val KEY_DATE_KEY = intPreferencesKey("ua_date_key")
        private val KEY_MOOD = stringPreferencesKey("ua_mood")
        private val KEY_USER_KNOWS = stringPreferencesKey("ua_user_knows")
        private val KEY_USER_DOESNT_KNOW = stringPreferencesKey("ua_user_doesnt_know")
        private val KEY_SHORT_RESPONSES = intPreferencesKey("ua_short_responses")
        private val KEY_ENGAGED_RESPONSES = intPreferencesKey("ua_engaged_responses")
        private val KEY_LAST_BUSY = longPreferencesKey("ua_last_busy")
        private val KEY_LAST_POSITIVE = longPreferencesKey("ua_last_positive")

        private const val SEPARATOR = "|||"
        private const val MAX_TOPICS = 30
    }
}
