package com.example.mydeskrobot.data.body

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.integration.body.BodyUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.bodySettingsDataStore by preferencesDataStore(name = "body_settings")

data class BodySettings(
    val enabled: Boolean = false,
    val baseUrl: String = "",
) {
    fun isConfigured(): Boolean = enabled && baseUrl.isNotBlank()
}

class BodySettingsRepository(
    private val context: Context,
) {
    val settings: Flow<BodySettings> =
        context.bodySettingsDataStore.data.map { prefs ->
            BodySettings(
                enabled = prefs[KEY_ENABLED] ?: false,
                baseUrl = prefs[KEY_BASE_URL]?.trim().orEmpty(),
            )
        }

    suspend fun load(): BodySettings = settings.first()

    suspend fun save(enabled: Boolean, baseUrl: String) {
        val normalized = BodyUrl.normalize(baseUrl)
        context.bodySettingsDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
            if (normalized.isBlank()) {
                prefs.remove(KEY_BASE_URL)
            } else {
                prefs[KEY_BASE_URL] = normalized
            }
        }
    }

    suspend fun updateBaseUrlFromStatus(urlIp: String?) {
        val normalized = BodyUrl.normalize(urlIp.orEmpty())
        if (normalized.isBlank()) return
        context.bodySettingsDataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = normalized
        }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
    }
}
