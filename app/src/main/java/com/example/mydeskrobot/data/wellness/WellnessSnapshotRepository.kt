package com.example.mydeskrobot.data.wellness

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.wellness.WellnessSnapshot
import com.example.mydeskrobot.domain.wellness.WellnessSnapshotParser
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val Context.wellnessSnapshotDataStore by preferencesDataStore(name = "wellness_snapshot")

/**
 * Persists the latest [WellnessSnapshot] (SSOT for UI / notify / dialogue inject).
 */
class WellnessSnapshotRepository(
    private val context: Context,
) {
    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    suspend fun getLatest(): WellnessSnapshot? {
        val raw = context.wellnessSnapshotDataStore.data.first()[KEY_SNAPSHOT] ?: return null
        return WellnessSnapshotParser.fromStorageJson(raw)
    }

    suspend fun save(snapshot: WellnessSnapshot) {
        context.wellnessSnapshotDataStore.edit { prefs ->
            prefs[KEY_SNAPSHOT] = WellnessSnapshotParser.toStorageJson(snapshot)
        }
    }

    suspend fun getNotifyDayKey(): String? =
        context.wellnessSnapshotDataStore.data.first()[KEY_NOTIFY_DAY]

    suspend fun markNotifyPosted(dayKey: String) {
        context.wellnessSnapshotDataStore.edit { prefs ->
            prefs[KEY_NOTIFY_DAY] = dayKey
        }
    }

    fun todayDayKey(nowMs: Long = System.currentTimeMillis()): String =
        dayKeyFormat.format(Date(nowMs))

    suspend fun canNotifyToday(dayKey: String = todayDayKey()): Boolean =
        getNotifyDayKey() != dayKey

    companion object {
        private val KEY_SNAPSHOT = stringPreferencesKey("latest_snapshot_json")
        private val KEY_NOTIFY_DAY = stringPreferencesKey("notify_posted_day_key")
    }
}
