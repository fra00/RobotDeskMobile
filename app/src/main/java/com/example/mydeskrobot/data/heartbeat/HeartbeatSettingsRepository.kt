package com.example.mydeskrobot.data.heartbeat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.heartbeatDataStore: DataStore<Preferences> by preferencesDataStore(name = "heartbeat_settings")

data class HeartbeatSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 10,
    val startHour: Int = 7,
    val endHour: Int = 23,
    val proactiveThreshold: Float = 0.75f,
    val lastInteractionMillis: Long = 0L,
)

class HeartbeatSettingsRepository(
    private val context: Context,
) {
    val settings: Flow<HeartbeatSettings> =
        context.heartbeatDataStore.data.map { prefs ->
            HeartbeatSettings(
                enabled = prefs[KEY_ENABLED] ?: false,
                intervalMinutes = prefs[KEY_INTERVAL_MINUTES] ?: 10,
                startHour = prefs[KEY_START_HOUR] ?: 7,
                endHour = prefs[KEY_END_HOUR] ?: 23,
                proactiveThreshold = prefs[KEY_PROACTIVE_THRESHOLD] ?: 0.75f,
                lastInteractionMillis = prefs[KEY_LAST_INTERACTION] ?: 0L,
            )
        }

    suspend fun load(): HeartbeatSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.heartbeatDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setIntervalMinutes(value: Int) {
        context.heartbeatDataStore.edit { it[KEY_INTERVAL_MINUTES] = value.coerceIn(5, 30) }
    }

    suspend fun setStartHour(value: Int) {
        context.heartbeatDataStore.edit { it[KEY_START_HOUR] = value.coerceIn(0, 23) }
    }

    suspend fun setEndHour(value: Int) {
        context.heartbeatDataStore.edit { it[KEY_END_HOUR] = value.coerceIn(0, 23) }
    }

    suspend fun setProactiveThreshold(value: Float) {
        context.heartbeatDataStore.edit { it[KEY_PROACTIVE_THRESHOLD] = value.coerceIn(0.5f, 1.0f) }
    }

    suspend fun recordInteraction() {
        context.heartbeatDataStore.edit { it[KEY_LAST_INTERACTION] = System.currentTimeMillis() }
    }

    suspend fun update(
        enabled: Boolean? = null,
        intervalMinutes: Int? = null,
        startHour: Int? = null,
        endHour: Int? = null,
        proactiveThreshold: Float? = null,
    ) {
        context.heartbeatDataStore.edit { prefs ->
            enabled?.let { prefs[KEY_ENABLED] = it }
            intervalMinutes?.let { prefs[KEY_INTERVAL_MINUTES] = it.coerceIn(5, 30) }
            startHour?.let { prefs[KEY_START_HOUR] = it.coerceIn(0, 23) }
            endHour?.let { prefs[KEY_END_HOUR] = it.coerceIn(0, 23) }
            proactiveThreshold?.let { prefs[KEY_PROACTIVE_THRESHOLD] = it.coerceIn(0.5f, 1.0f) }
        }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("heartbeat_enabled")
        private val KEY_INTERVAL_MINUTES = intPreferencesKey("heartbeat_interval_minutes")
        private val KEY_START_HOUR = intPreferencesKey("heartbeat_start_hour")
        private val KEY_END_HOUR = intPreferencesKey("heartbeat_end_hour")
        private val KEY_PROACTIVE_THRESHOLD = floatPreferencesKey("heartbeat_proactive_threshold")
        private val KEY_LAST_INTERACTION = longPreferencesKey("heartbeat_last_interaction")
    }
}
