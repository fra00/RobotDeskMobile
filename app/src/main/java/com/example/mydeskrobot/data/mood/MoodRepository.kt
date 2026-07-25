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
import com.example.mydeskrobot.domain.mood.MoodDelta
import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.domain.mood.MoodValenceConfig
import com.example.mydeskrobot.domain.mood.RobotMood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.moodDataStore: DataStore<Preferences> by preferencesDataStore(name = "robot_mood")

class MoodRepository(
    private val context: Context,
) {
    val mood: Flow<RobotMood> =
        context.moodDataStore.data.map { prefs -> parseMood(prefs) }

    suspend fun load(): RobotMood = mood.first()

    suspend fun save(mood: RobotMood) {
        context.moodDataStore.edit { prefs ->
            prefs[KEY_VALENCE] = mood.valence
            prefs[KEY_BASELINE] = mood.baseline
            prefs[KEY_EMOTION] = mood.baseEmotion.name
            prefs[KEY_INTENSITY] = mood.intensity
            prefs[KEY_SINCE] = mood.since
            prefs[KEY_LAST_DECAY_AT] = mood.lastDecayAtMs
            if (mood.reason != null) {
                prefs[KEY_REASON] = mood.reason.name
            } else {
                prefs.remove(KEY_REASON)
            }
            prefs[KEY_RECENT_DELTAS] = serializeDeltas(mood.recentDeltas)
        }
    }

    suspend fun reset() {
        context.moodDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun parseMood(prefs: Preferences): RobotMood {
        val since = prefs[KEY_SINCE] ?: System.currentTimeMillis()
        val lastDecayAt = prefs[KEY_LAST_DECAY_AT] ?: since
        val reasonName = prefs[KEY_REASON]
        val reason = reasonName?.let {
            runCatching { MoodReason.valueOf(it) }.getOrNull()
        }

        val storedValence = prefs[KEY_VALENCE]
        if (storedValence != null) {
            return RobotMood.fromValence(
                valence = storedValence,
                baseline = prefs[KEY_BASELINE] ?: MoodValenceConfig.DEFAULT_BASELINE,
                since = since,
                reason = reason,
                recentDeltas = parseDeltas(prefs[KEY_RECENT_DELTAS]),
                lastDecayAtMs = lastDecayAt,
            )
        }

        val emotionName = prefs[KEY_EMOTION] ?: RobotEmotion.NEUTRAL.name
        val intensity = prefs[KEY_INTENSITY] ?: 0.5f
        val emotion = runCatching { RobotEmotion.valueOf(emotionName) }
            .getOrDefault(RobotEmotion.NEUTRAL)
        return RobotMood.fromLegacy(emotion, intensity, since, reason)
    }

    private fun serializeDeltas(deltas: List<MoodDelta>): String =
        deltas.joinToString(";") { "${it.event}|${it.delta}|${it.at}" }

    private fun parseDeltas(raw: String?): List<MoodDelta> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val pieces = part.split("|")
            if (pieces.size != 3) return@mapNotNull null
            val delta = pieces[1].toFloatOrNull() ?: return@mapNotNull null
            val at = pieces[2].toLongOrNull() ?: return@mapNotNull null
            MoodDelta(event = pieces[0], delta = delta, at = at)
        }
    }

    companion object {
        private val KEY_VALENCE = floatPreferencesKey("mood_valence")
        private val KEY_BASELINE = floatPreferencesKey("mood_baseline")
        private val KEY_EMOTION = stringPreferencesKey("mood_emotion")
        private val KEY_INTENSITY = floatPreferencesKey("mood_intensity")
        private val KEY_SINCE = longPreferencesKey("mood_since")
        private val KEY_LAST_DECAY_AT = longPreferencesKey("mood_last_decay_at")
        private val KEY_REASON = stringPreferencesKey("mood_reason")
        private val KEY_RECENT_DELTAS = stringPreferencesKey("mood_recent_deltas")
    }
}
