package com.example.mydeskrobot.data.proactive

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.proactive.ProactivityConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.proactivitySettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "proactivity_settings_v1",
)

data class ProactivitySettings(
    val predictivityEnabled: Boolean = true,
    val wellnessEnabled: Boolean = true,
    val wellnessAnchorMinutes: Int = ProactivityConstants.WELLNESS_ANCHOR_MINUTES,
    val wellnessIdleMinutes: Int = ProactivityConstants.WELLNESS_IDLE_MINUTES,
    val wellnessPresenceMinutes: Int = ProactivityConstants.WELLNESS_PRESENCE_MINUTES,
    /**
     * Auto DOMAIN_SCORE again when today's snapshot is at least this old (minutes).
     * Visual order remains once/day; notify remains capped once/day.
     */
    val wellnessRescoreIntervalMinutes: Int = ProactivityConstants.WELLNESS_RESCORE_INTERVAL_MINUTES,
    /** Sync exercise sessions from Health Connect into Log Day (default off until user opts in). */
    val healthConnectEnabled: Boolean = false,
)

class ProactivitySettingsRepository(
    private val context: Context,
) {
    val settings: Flow<ProactivitySettings> =
        context.proactivitySettingsDataStore.data.map { prefs ->
            ProactivitySettings(
                predictivityEnabled = prefs[KEY_PREDICTIVITY_ENABLED] ?: true,
                wellnessEnabled = prefs[KEY_WELLNESS_ENABLED] ?: true,
                wellnessAnchorMinutes = prefs[KEY_WELLNESS_ANCHOR_MINUTES]
                    ?: ProactivityConstants.WELLNESS_ANCHOR_MINUTES,
                wellnessIdleMinutes = prefs[KEY_WELLNESS_IDLE_MINUTES]
                    ?: ProactivityConstants.WELLNESS_IDLE_MINUTES,
                wellnessPresenceMinutes = prefs[KEY_WELLNESS_PRESENCE_MINUTES]
                    ?: ProactivityConstants.WELLNESS_PRESENCE_MINUTES,
                wellnessRescoreIntervalMinutes = prefs[KEY_WELLNESS_RESCORE_INTERVAL_MINUTES]
                    ?: ProactivityConstants.WELLNESS_RESCORE_INTERVAL_MINUTES,
                healthConnectEnabled = prefs[KEY_HEALTH_CONNECT_ENABLED] ?: false,
            )
        }

    suspend fun load(): ProactivitySettings = settings.first()

    suspend fun update(
        predictivityEnabled: Boolean? = null,
        wellnessEnabled: Boolean? = null,
        wellnessAnchorMinutes: Int? = null,
        wellnessIdleMinutes: Int? = null,
        wellnessPresenceMinutes: Int? = null,
        wellnessRescoreIntervalMinutes: Int? = null,
        healthConnectEnabled: Boolean? = null,
    ) {
        context.proactivitySettingsDataStore.edit { prefs ->
            predictivityEnabled?.let { prefs[KEY_PREDICTIVITY_ENABLED] = it }
            wellnessEnabled?.let { prefs[KEY_WELLNESS_ENABLED] = it }
            wellnessAnchorMinutes?.let {
                prefs[KEY_WELLNESS_ANCHOR_MINUTES] = it.coerceIn(15, 180)
            }
            wellnessIdleMinutes?.let {
                prefs[KEY_WELLNESS_IDLE_MINUTES] = it.coerceIn(5, 120)
            }
            wellnessPresenceMinutes?.let {
                prefs[KEY_WELLNESS_PRESENCE_MINUTES] = it.coerceIn(5, 60)
            }
            wellnessRescoreIntervalMinutes?.let {
                prefs[KEY_WELLNESS_RESCORE_INTERVAL_MINUTES] = it.coerceIn(60, 720)
            }
            healthConnectEnabled?.let { prefs[KEY_HEALTH_CONNECT_ENABLED] = it }
        }
    }

    companion object {
        private val KEY_PREDICTIVITY_ENABLED = booleanPreferencesKey("predictivity_enabled")
        private val KEY_WELLNESS_ENABLED = booleanPreferencesKey("wellness_enabled")
        private val KEY_WELLNESS_ANCHOR_MINUTES = intPreferencesKey("wellness_anchor_minutes")
        private val KEY_WELLNESS_IDLE_MINUTES = intPreferencesKey("wellness_idle_minutes")
        private val KEY_WELLNESS_PRESENCE_MINUTES = intPreferencesKey("wellness_presence_minutes")
        private val KEY_WELLNESS_RESCORE_INTERVAL_MINUTES =
            intPreferencesKey("wellness_rescore_interval_minutes")
        private val KEY_HEALTH_CONNECT_ENABLED = booleanPreferencesKey("health_connect_enabled")
    }
}
