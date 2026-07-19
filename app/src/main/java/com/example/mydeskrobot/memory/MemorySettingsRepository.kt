package com.example.mydeskrobot.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    val autoReorganizeEnabled: Boolean = true,
    val reorganizeMinRows: Int = MemoryReorganizePolicy.DEFAULT_MIN_USER_FACING_ROWS,
    val reorganizeCooldownDays: Long = MemoryReorganizePolicy.DEFAULT_COOLDOWN_DAYS,
)

class MemorySettingsRepository(
    private val context: Context,
) : MemoryConsolidationSettingsStore, MemoryReorganizeSettingsStore {
    val settings: Flow<MemorySettings> =
        context.memoryDataStore.data.map { prefs ->
            MemorySettings(
                enabled = prefs[KEY_ENABLED] ?: true,
                intervalSeconds = prefs[KEY_INTERVAL_SECONDS] ?: 45L,
                lastProcessedEntryCount = prefs[KEY_LAST_PROCESSED_ID] ?: 0L,
                autoReorganizeEnabled = prefs[KEY_AUTO_REORGANIZE_ENABLED] ?: true,
                reorganizeMinRows = prefs[KEY_REORGANIZE_MIN_ROWS]
                    ?: MemoryReorganizePolicy.DEFAULT_MIN_USER_FACING_ROWS,
                reorganizeCooldownDays = prefs[KEY_REORGANIZE_COOLDOWN_DAYS]
                    ?: MemoryReorganizePolicy.DEFAULT_COOLDOWN_DAYS,
            )
        }

    suspend fun load(): MemorySettings = settings.first()

    override suspend fun loadReorganizeConfig(): MemoryReorganizeConfig {
        val settings = load()
        return MemoryReorganizeConfig(
            autoReorganizeEnabled = settings.autoReorganizeEnabled,
            minUserFacingRows = settings.reorganizeMinRows,
            cooldownDays = settings.reorganizeCooldownDays,
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.memoryDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setIntervalSeconds(value: Long) {
        context.memoryDataStore.edit { it[KEY_INTERVAL_SECONDS] = value.coerceIn(10L, 300L) }
    }

    suspend fun setAutoReorganizeEnabled(enabled: Boolean) {
        context.memoryDataStore.edit { it[KEY_AUTO_REORGANIZE_ENABLED] = enabled }
    }

    suspend fun setReorganizeMinRows(value: Int) {
        context.memoryDataStore.edit {
            it[KEY_REORGANIZE_MIN_ROWS] = value.coerceIn(10, 500)
        }
    }

    suspend fun setReorganizeCooldownDays(value: Long) {
        context.memoryDataStore.edit {
            it[KEY_REORGANIZE_COOLDOWN_DAYS] = value.coerceIn(1L, 90L)
        }
    }

    suspend fun setLastProcessedEntryCount(value: Long) {
        context.memoryDataStore.edit { it[KEY_LAST_PROCESSED_ID] = maxOf(0L, value) }
    }

    override suspend fun getLastConsolidatedContentHash(): String? =
        context.memoryDataStore.data.first()[KEY_LAST_CONSOLIDATED_HASH]

    override suspend fun setLastConsolidatedContentHash(hash: String) {
        context.memoryDataStore.edit { it[KEY_LAST_CONSOLIDATED_HASH] = hash }
    }

    suspend fun isUnifiedMemoryMigrated(): Boolean =
        context.memoryDataStore.data.first()[KEY_UNIFIED_MEMORY_MIGRATED] ?: false

    suspend fun setUnifiedMemoryMigrated(migrated: Boolean) {
        context.memoryDataStore.edit { it[KEY_UNIFIED_MEMORY_MIGRATED] = migrated }
    }

    suspend fun isUnifiedProjectionsMigrated(): Boolean =
        context.memoryDataStore.data.first()[KEY_UNIFIED_PROJECTIONS_MIGRATED] ?: false

    suspend fun setUnifiedProjectionsMigrated(migrated: Boolean) {
        context.memoryDataStore.edit { it[KEY_UNIFIED_PROJECTIONS_MIGRATED] = migrated }
    }

    suspend fun getProjectionDriftCount(): Long =
        context.memoryDataStore.data.first()[KEY_PROJECTION_DRIFT_COUNT] ?: 0L

    suspend fun getLastProjectionDriftAtMs(): Long =
        context.memoryDataStore.data.first()[KEY_LAST_PROJECTION_DRIFT_AT_MS] ?: 0L

    suspend fun getLastProjectionReconcileAtMs(): Long =
        context.memoryDataStore.data.first()[KEY_LAST_PROJECTION_RECONCILE_AT_MS] ?: 0L

    suspend fun recordProjectionDrift() {
        val now = System.currentTimeMillis()
        context.memoryDataStore.edit { prefs ->
            val current = prefs[KEY_PROJECTION_DRIFT_COUNT] ?: 0L
            prefs[KEY_PROJECTION_DRIFT_COUNT] = current + 1L
            prefs[KEY_LAST_PROJECTION_DRIFT_AT_MS] = now
        }
    }

    suspend fun setLastProjectionReconcileAtMs(value: Long) {
        context.memoryDataStore.edit {
            it[KEY_LAST_PROJECTION_RECONCILE_AT_MS] = maxOf(0L, value)
        }
    }

    override suspend fun setLastManualReorganizeAtMs(value: Long) {
        context.memoryDataStore.edit {
            it[KEY_LAST_MANUAL_REORGANIZE_AT_MS] = maxOf(0L, value)
        }
    }

    override suspend fun getLastManualReorganizeAtMs(): Long? {
        val value = context.memoryDataStore.data.first()[KEY_LAST_MANUAL_REORGANIZE_AT_MS]
        return value?.takeIf { it > 0L }
    }

    override suspend fun saveConsolidationBackup(items: List<MemoryItemEntity>) {
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
        private val KEY_AUTO_REORGANIZE_ENABLED = booleanPreferencesKey("auto_reorganize_enabled")
        private val KEY_REORGANIZE_MIN_ROWS = intPreferencesKey("reorganize_min_rows")
        private val KEY_REORGANIZE_COOLDOWN_DAYS = longPreferencesKey("reorganize_cooldown_days")
        private val KEY_LAST_PROCESSED_ID = longPreferencesKey("last_processed_message_id")
        private val KEY_LAST_MANUAL_REORGANIZE_AT_MS = longPreferencesKey("last_manual_reorganize_at_ms")
        private val KEY_LAST_CONSOLIDATED_HASH = stringPreferencesKey("last_consolidated_content_hash")
        private val KEY_LAST_CONSOLIDATION_BACKUP = stringPreferencesKey("last_consolidation_backup_json")
        private val KEY_UNIFIED_MEMORY_MIGRATED = booleanPreferencesKey("unified_memory_migrated")
        private val KEY_UNIFIED_PROJECTIONS_MIGRATED = booleanPreferencesKey("unified_projections_migrated")
        private val KEY_PROJECTION_DRIFT_COUNT = longPreferencesKey("projection_drift_count")
        private val KEY_LAST_PROJECTION_DRIFT_AT_MS = longPreferencesKey("last_projection_drift_at_ms")
        private val KEY_LAST_PROJECTION_RECONCILE_AT_MS = longPreferencesKey("last_projection_reconcile_at_ms")

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
