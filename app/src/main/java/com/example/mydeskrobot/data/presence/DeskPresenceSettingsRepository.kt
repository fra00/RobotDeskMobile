package com.example.mydeskrobot.data.presence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deskPresenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "desk_presence_settings",
)

data class DeskPresenceSettings(
    val enabled: Boolean = true,
    /** Target analysis rate while voice session is active (device is expected on charger). */
    val analysisFps: Int = 5,
    val faceConfidenceThreshold: Float = 0.6f,
)

class DeskPresenceSettingsRepository(
    private val context: Context,
) {
    val settings: Flow<DeskPresenceSettings> =
        context.deskPresenceDataStore.data.map { prefs ->
            DeskPresenceSettings(
                enabled = prefs[KEY_ENABLED] ?: true,
                analysisFps = prefs[KEY_ANALYSIS_FPS] ?: 5,
                faceConfidenceThreshold = prefs[KEY_FACE_THRESHOLD] ?: 0.6f,
            )
        }

    suspend fun load(): DeskPresenceSettings = settings.first()

    suspend fun update(
        enabled: Boolean? = null,
        analysisFps: Int? = null,
        faceConfidenceThreshold: Float? = null,
    ) {
        context.deskPresenceDataStore.edit { prefs ->
            enabled?.let { prefs[KEY_ENABLED] = it }
            analysisFps?.let { prefs[KEY_ANALYSIS_FPS] = it.coerceIn(2, 10) }
            faceConfidenceThreshold?.let {
                prefs[KEY_FACE_THRESHOLD] = it.coerceIn(0.4f, 0.95f)
            }
        }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("desk_presence_enabled")
        private val KEY_ANALYSIS_FPS = intPreferencesKey("desk_presence_analysis_fps")
        private val KEY_FACE_THRESHOLD = floatPreferencesKey("desk_presence_face_threshold")
    }
}
