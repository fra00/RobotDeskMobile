package com.example.mydeskrobot.data.input

import android.content.Context
import android.content.ComponentName
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.inputSettingsDataStore by preferencesDataStore(name = "input_settings")

/**
 * Repository for notification input settings.
 * Stores whether notifications are enabled and which apps are allowed.
 */
class InputSettingsRepository(
    private val context: Context,
) {
    private val dataStore = context.inputSettingsDataStore

    companion object {
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")

        /**
         * Default allowed packages (popular messaging apps).
         */
        val DEFAULT_ALLOWED_PACKAGES = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.google.android.gm",
            "com.google.android.calendar",
        )

        /**
         * Known packages with human-readable labels.
         */
        val KNOWN_APPS = mapOf(
            "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram",
            "com.google.android.apps.messaging" to "Messaggi (Google)",
            "com.google.android.gm" to "Gmail",
            "com.google.android.calendar" to "Calendario (Google)",
            "com.facebook.orca" to "Messenger",
            "com.instagram.android" to "Instagram",
            "com.twitter.android" to "X (Twitter)",
            "com.linkedin.android" to "LinkedIn",
        )
    }

    suspend fun isNotificationsEnabled(): Boolean {
        return dataStore.data.map { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] ?: false
        }.first()
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun getAllowedPackages(): Set<String> {
        return dataStore.data.map { prefs ->
            prefs[KEY_ALLOWED_PACKAGES] ?: DEFAULT_ALLOWED_PACKAGES
        }.first()
    }

    suspend fun setAllowedPackages(packages: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_ALLOWED_PACKAGES] = packages
        }
    }

    suspend fun isPackageAllowed(packageName: String): Boolean {
        return getAllowedPackages().contains(packageName)
    }

    suspend fun togglePackage(packageName: String) {
        val current = getAllowedPackages().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setAllowedPackages(current)
    }

    /**
     * Check if the app has notification listener permission.
     */
    fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val componentName = ComponentName(context, NOTIFICATION_SERVICE_CLASS)
        return flat.contains(componentName.flattenToString())
    }
}

private const val NOTIFICATION_SERVICE_CLASS =
    "com.example.mydeskrobot.integration.input.notification.RobotNotificationListenerService"
