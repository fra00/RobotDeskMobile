package com.example.mydeskrobot.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.memoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "memory_settings")

data class MemorySettings(
    val enabled: Boolean = true,
    val intervalSeconds: Long = 45L,
    /** Number of parsed log entries already sent to the memory extractor. */
    val lastProcessedEntryCount: Long = 0L,
)

class MemorySettingsRepository(
    private val context: Context,
) {
    val settings: Flow<MemorySettings> =
        context.memoryDataStore.data.map { prefs ->
            MemorySettings(
                enabled = prefs[KEY_ENABLED] ?: true,
                intervalSeconds = prefs[KEY_INTERVAL_SECONDS] ?: 45L,
                lastProcessedEntryCount = prefs[KEY_LAST_PROCESSED_ID] ?: 0L,
            )
        }

    suspend fun load(): MemorySettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.memoryDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setIntervalSeconds(value: Long) {
        context.memoryDataStore.edit { it[KEY_INTERVAL_SECONDS] = value.coerceIn(10L, 300L) }
    }

    suspend fun setLastProcessedEntryCount(value: Long) {
        context.memoryDataStore.edit { it[KEY_LAST_PROCESSED_ID] = maxOf(0L, value) }
    }

    suspend fun getLastConsolidatedContentHash(): String? =
        context.memoryDataStore.data.first()[KEY_LAST_CONSOLIDATED_HASH]

    suspend fun setLastConsolidatedContentHash(hash: String) {
        context.memoryDataStore.edit { it[KEY_LAST_CONSOLIDATED_HASH] = hash }
    }

    suspend fun saveConsolidationBackup(items: List<MemoryItemEntity>) {
        val snapshot = MemoryConsolidationBackup(
            savedAtMs = System.currentTimeMillis(),
            items = items.map { item ->
                MemoryConsolidationBackupItem(
                    category = item.category.name,
                    value = item.value,
                    confidence = item.confidence,
                )
            },
        )
        val json = backupAdapter.toJson(snapshot)
        context.memoryDataStore.edit { it[KEY_LAST_CONSOLIDATION_BACKUP] = json }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_INTERVAL_SECONDS = longPreferencesKey("interval_seconds")
        private val KEY_LAST_PROCESSED_ID = longPreferencesKey("last_processed_message_id")
        private val KEY_LAST_CONSOLIDATED_HASH = stringPreferencesKey("last_consolidated_content_hash")
        private val KEY_LAST_CONSOLIDATION_BACKUP = stringPreferencesKey("last_consolidation_backup_json")

        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        private val backupAdapter = moshi.adapter(MemoryConsolidationBackup::class.java)
    }
}

@JsonClass(generateAdapter = true)
internal data class MemoryConsolidationBackup(
    val savedAtMs: Long,
    val items: List<MemoryConsolidationBackupItem>,
)

@JsonClass(generateAdapter = true)
internal data class MemoryConsolidationBackupItem(
    val category: String,
    val value: String,
    val confidence: Float,
)
