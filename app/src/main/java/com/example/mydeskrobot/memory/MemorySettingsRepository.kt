package com.example.mydeskrobot.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.memoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "memory_settings")

data class MemorySettings(
    val enabled: Boolean = true,
    val intervalSeconds: Long = 45L,
    val lastProcessedMessageId: Long = 0L,
)

class MemorySettingsRepository(
    private val context: Context,
) {
    val settings: Flow<MemorySettings> =
        context.memoryDataStore.data.map { prefs ->
            MemorySettings(
                enabled = prefs[KEY_ENABLED] ?: true,
                intervalSeconds = prefs[KEY_INTERVAL_SECONDS] ?: 45L,
                lastProcessedMessageId = prefs[KEY_LAST_PROCESSED_ID] ?: 0L,
            )
        }

    suspend fun load(): MemorySettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.memoryDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setIntervalSeconds(value: Long) {
        context.memoryDataStore.edit { it[KEY_INTERVAL_SECONDS] = value.coerceIn(10L, 300L) }
    }

    suspend fun setLastProcessedMessageId(value: Long) {
        context.memoryDataStore.edit { it[KEY_LAST_PROCESSED_ID] = maxOf(0L, value) }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_INTERVAL_SECONDS = longPreferencesKey("interval_seconds")
        private val KEY_LAST_PROCESSED_ID = longPreferencesKey("last_processed_message_id")
    }
}
