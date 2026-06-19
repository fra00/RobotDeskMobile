package com.example.mydeskrobot.data.pending

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.pending.UnannouncedNotification
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.unannouncedNotificationsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "unannounced_notifications",
)

/**
 * Notifications already processed by the LLM without TTS — pending optional user replay.
 */
class UnannouncedNotificationRepository(
    private val context: Context,
) {
    val notifications: Flow<List<UnannouncedNotification>> =
        context.unannouncedNotificationsDataStore.data.map { prefs ->
            decode(prefs[KEY_ITEMS_JSON])
        }

    suspend fun getAll(): List<UnannouncedNotification> = notifications.first()

    suspend fun register(
        appLabel: String,
        title: String?,
        text: String?,
        packageName: String,
        receivedAtMillis: Long,
        dedupKey: String,
        robotSummary: String?,
    ): UnannouncedNotification? {
        val current = getAll().toMutableList()
        if (current.any { it.dedupKey == dedupKey }) {
            return null
        }
        val item = UnannouncedNotification(
            id = UUID.randomUUID().toString(),
            appLabel = appLabel,
            title = title,
            text = text,
            packageName = packageName,
            receivedAtMillis = receivedAtMillis,
            dedupKey = dedupKey,
            robotSummary = robotSummary?.trim()?.takeIf { it.isNotBlank() },
        )
        current.add(item)
        while (current.size > MAX_ITEMS) {
            current.removeAt(0)
        }
        save(current)
        return item
    }

    suspend fun remove(id: String): Boolean {
        val current = getAll()
        if (current.none { it.id == id }) return false
        save(current.filterNot { it.id == id })
        return true
    }

    suspend fun clearAll() {
        save(emptyList())
    }

    private suspend fun save(items: List<UnannouncedNotification>) {
        val snapshot = UnannouncedNotificationSnapshot(
            items = items.map { item ->
                UnannouncedNotificationSnapshotItem(
                    id = item.id,
                    appLabel = item.appLabel,
                    title = item.title,
                    text = item.text,
                    packageName = item.packageName,
                    receivedAtMillis = item.receivedAtMillis,
                    dedupKey = item.dedupKey,
                    robotSummary = item.robotSummary,
                )
            },
        )
        val json = adapter.toJson(snapshot)
        context.unannouncedNotificationsDataStore.edit { it[KEY_ITEMS_JSON] = json }
    }

    private fun decode(json: String?): List<UnannouncedNotification> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            adapter.fromJson(json)?.items?.map { row ->
                UnannouncedNotification(
                    id = row.id,
                    appLabel = row.appLabel,
                    title = row.title,
                    text = row.text,
                    packageName = row.packageName,
                    receivedAtMillis = row.receivedAtMillis,
                    dedupKey = row.dedupKey,
                    robotSummary = row.robotSummary,
                )
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_ITEMS = 50
        private val KEY_ITEMS_JSON = stringPreferencesKey("items_json")

        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        private val adapter = moshi.adapter(UnannouncedNotificationSnapshot::class.java)
    }
}

@JsonClass(generateAdapter = true)
private data class UnannouncedNotificationSnapshot(
    val items: List<UnannouncedNotificationSnapshotItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
private data class UnannouncedNotificationSnapshotItem(
    val id: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val packageName: String,
    val receivedAtMillis: Long,
    val dedupKey: String,
    val robotSummary: String? = null,
)
