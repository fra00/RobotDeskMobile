package com.example.mydeskrobot.data.activitylog

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.activityLogDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "activity_log_settings",
)

data class ActivityLogSettings(
    val enabled: Boolean = true,
    val intervalMinutes: Long = 15L,
    val lastProcessedEntryCount: Long = 0L,
    val lastSummaryAtMs: Long = 0L,
    val lastSummaryEventCount: Int = 0,
)

class ActivityLogSettingsRepository(
    private val context: Context,
) {
    val settings: Flow<ActivityLogSettings> =
        context.activityLogDataStore.data.map { prefs ->
            ActivityLogSettings(
                enabled = prefs[KEY_ENABLED] ?: true,
                intervalMinutes = prefs[KEY_INTERVAL_MINUTES] ?: 15L,
                lastProcessedEntryCount = prefs[KEY_LAST_PROCESSED_ID] ?: 0L,
                lastSummaryAtMs = prefs[KEY_LAST_SUMMARY_AT] ?: 0L,
                lastSummaryEventCount = prefs[KEY_LAST_SUMMARY_EVENT_COUNT] ?: 0,
            )
        }

    suspend fun load(): ActivityLogSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.activityLogDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setIntervalMinutes(value: Long) {
        context.activityLogDataStore.edit {
            it[KEY_INTERVAL_MINUTES] = value.coerceIn(5L, 120L)
        }
    }

    suspend fun setLastProcessedEntryCount(value: Long) {
        context.activityLogDataStore.edit { it[KEY_LAST_PROCESSED_ID] = maxOf(0L, value) }
    }

    suspend fun setLastSummaryAt(value: Long) {
        context.activityLogDataStore.edit { it[KEY_LAST_SUMMARY_AT] = maxOf(0L, value) }
    }

    suspend fun setLastSummaryEventCount(value: Int) {
        context.activityLogDataStore.edit { it[KEY_LAST_SUMMARY_EVENT_COUNT] = maxOf(0, value) }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_INTERVAL_MINUTES = longPreferencesKey("interval_minutes")
        private val KEY_LAST_PROCESSED_ID = longPreferencesKey("last_processed_entry_count")
        private val KEY_LAST_SUMMARY_AT = longPreferencesKey("last_summary_at_ms")
        private val KEY_LAST_SUMMARY_EVENT_COUNT = intPreferencesKey("last_summary_event_count")
    }
}
