package com.example.mydeskrobot.data.mood

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.domain.mood.RobotMood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.moodDataStore: DataStore<Preferences> by preferencesDataStore(name = "robot_mood")

/**
 * Persists the robot's autonomous mood state.
 */
class MoodRepository(
    private val context: Context,
) {
    val mood: Flow<RobotMood> =
        context.moodDataStore.data.map { prefs ->
            val emotionName = prefs[KEY_EMOTION] ?: RobotEmotion.NEUTRAL.name
            val intensity = prefs[KEY_INTENSITY] ?: 0.5f
            val since = prefs[KEY_SINCE] ?: System.currentTimeMillis()
            val reasonName = prefs[KEY_REASON]

            val emotion = runCatching { RobotEmotion.valueOf(emotionName) }
                .getOrDefault(RobotEmotion.NEUTRAL)
            val reason = reasonName?.let {
                runCatching { MoodReason.valueOf(it) }.getOrNull()
            }

            RobotMood(
                baseEmotion = emotion,
                intensity = intensity.coerceIn(0f, 1f),
                since = since,
                reason = reason,
            )
        }

    suspend fun load(): RobotMood = mood.first()

    suspend fun save(mood: RobotMood) {
        context.moodDataStore.edit { prefs ->
            prefs[KEY_EMOTION] = mood.baseEmotion.name
            prefs[KEY_INTENSITY] = mood.intensity
            prefs[KEY_SINCE] = mood.since
            if (mood.reason != null) {
                prefs[KEY_REASON] = mood.reason.name
            } else {
                prefs.remove(KEY_REASON)
            }
        }
    }

    suspend fun reset() {
        context.moodDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        private val KEY_EMOTION = stringPreferencesKey("mood_emotion")
        private val KEY_INTENSITY = floatPreferencesKey("mood_intensity")
        private val KEY_SINCE = longPreferencesKey("mood_since")
        private val KEY_REASON = stringPreferencesKey("mood_reason")
    }
}
