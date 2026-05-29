package com.example.mydeskrobot.data.context

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.context.RobotContextPolicy
import com.example.mydeskrobot.integration.context.RobotContextExpiryReceiver
import com.example.mydeskrobot.reasoning.model.NotificationMode
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.reasoning.model.RobotProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

private val Context.robotContextDataStore by preferencesDataStore(name = "robot_context")

class RobotContextRepository(
    private val context: Context,
) {
    private val dataStore = context.robotContextDataStore
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val requestCode = AtomicInteger(8000)

    companion object {
        private const val TAG = "RobotContextRepo"

        private val KEY_PROFILE = stringPreferencesKey("profile")
        private val KEY_NOTIFICATION_MODE = stringPreferencesKey("notification_mode")
        private val KEY_SESSION_ONLY = booleanPreferencesKey("session_only")
        private val KEY_VALID_UNTIL = longPreferencesKey("valid_until")
        private val KEY_WINDOW_START = intPreferencesKey("window_start")
        private val KEY_WINDOW_END = intPreferencesKey("window_end")
        private val KEY_ALARM_REQUEST_CODE = intPreferencesKey("alarm_request_code")
    }

    suspend fun getState(): RobotContextState {
        val stored = dataStore.data.map { prefs ->
            RobotContextState(
                profile = parseProfile(prefs[KEY_PROFILE]),
                notificationMode = parseNotificationMode(prefs[KEY_NOTIFICATION_MODE]),
                sessionOnly = prefs[KEY_SESSION_ONLY] ?: false,
                validUntilEpochMs = prefs[KEY_VALID_UNTIL],
                windowStartMinutes = prefs[KEY_WINDOW_START],
                windowEndMinutes = prefs[KEY_WINDOW_END],
            )
        }.first()
        return RobotContextPolicy.resolveEffectiveState(stored)
    }

    suspend fun getStoredState(): RobotContextState {
        return dataStore.data.map { prefs ->
            RobotContextState(
                profile = parseProfile(prefs[KEY_PROFILE]),
                notificationMode = parseNotificationMode(prefs[KEY_NOTIFICATION_MODE]),
                sessionOnly = prefs[KEY_SESSION_ONLY] ?: false,
                validUntilEpochMs = prefs[KEY_VALID_UNTIL],
                windowStartMinutes = prefs[KEY_WINDOW_START],
                windowEndMinutes = prefs[KEY_WINDOW_END],
            )
        }.first()
    }

    suspend fun setState(state: RobotContextState) {
        cancelScheduledExpiry()
        dataStore.edit { prefs ->
            prefs[KEY_PROFILE] = state.profile.name
            prefs[KEY_NOTIFICATION_MODE] = state.notificationMode.name
            prefs[KEY_SESSION_ONLY] = state.sessionOnly
            if (state.validUntilEpochMs != null) {
                prefs[KEY_VALID_UNTIL] = state.validUntilEpochMs
            } else {
                prefs.remove(KEY_VALID_UNTIL)
            }
            if (state.windowStartMinutes != null) {
                prefs[KEY_WINDOW_START] = state.windowStartMinutes
            } else {
                prefs.remove(KEY_WINDOW_START)
            }
            if (state.windowEndMinutes != null) {
                prefs[KEY_WINDOW_END] = state.windowEndMinutes
            } else {
                prefs.remove(KEY_WINDOW_END)
            }
        }
        state.validUntilEpochMs?.let { scheduleExpiry(it) }
    }

    suspend fun clearToNormal() {
        cancelScheduledExpiry()
        dataStore.edit { prefs ->
            prefs.clear()
        }
        Log.i(TAG, "Robot context cleared to NORMAL")
    }

    suspend fun applyFromToolParams(params: Map<String, Any?>): RobotContextState {
        val profileRaw = params["profile"]?.toString()?.trim()?.lowercase()
        if (profileRaw == "normal") {
            clearToNormal()
            return RobotContextState.NORMAL
        }

        val profile = when (profileRaw) {
            null, "" -> RobotProfile.NORMAL
            else -> parseProfileParam(profileRaw)
        }

        val notificationOverride = params["notifications"]?.toString()?.trim()?.lowercase()
        val notificationMode = when (notificationOverride) {
            "silent" -> NotificationMode.SILENT
            "normal" -> NotificationMode.NORMAL
            else -> if (profile == RobotProfile.NORMAL && profileRaw.isNullOrEmpty()) {
                NotificationMode.NORMAL
            } else {
                RobotContextPolicy.defaultNotificationModeFor(profile)
            }
        }

        val sessionOnly = params["session_only"] as? Boolean
            ?: params["session_only"]?.toString()?.toBooleanStrictOrNull()
            ?: false

        val durationMinutes = params["duration_minutes"]?.toString()?.toIntOrNull()
        val untilHour = params["until_hour"]?.toString()?.toIntOrNull()
        val untilMinute = params["until_minute"]?.toString()?.toIntOrNull()

        val windowStart = toMinutes(
            params["window_start_hour"]?.toString()?.toIntOrNull(),
            params["window_start_minute"]?.toString()?.toIntOrNull(),
        )
        val windowEnd = toMinutes(
            params["window_end_hour"]?.toString()?.toIntOrNull(),
            params["window_end_minute"]?.toString()?.toIntOrNull(),
        )

        val validUntil = when {
            durationMinutes != null && durationMinutes > 0 ->
                System.currentTimeMillis() + durationMinutes * 60_000L
            untilHour != null && untilMinute != null ->
                computeUntilEpochMs(untilHour, untilMinute)
            else -> null
        }

        val state = RobotContextState(
            profile = profile,
            notificationMode = notificationMode,
            sessionOnly = sessionOnly,
            validUntilEpochMs = validUntil,
            windowStartMinutes = windowStart,
            windowEndMinutes = windowEnd,
        )
        setState(state)
        return RobotContextPolicy.resolveEffectiveState(state)
    }

    fun shouldDropNotificationsNow(): Boolean {
        return kotlinx.coroutines.runBlocking {
            RobotContextPolicy.shouldDropNotifications(getStoredState())
        }
    }

    private fun scheduleExpiry(epochMs: Long) {
        val code = requestCode.incrementAndGet()
        val intent = Intent(context, RobotContextExpiryReceiver::class.java).apply {
            action = RobotContextExpiryReceiver.ACTION_CLEAR
        }
        val pending = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        kotlinx.coroutines.runBlocking {
            dataStore.edit { it[KEY_ALARM_REQUEST_CODE] = code }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, epochMs, pending)
            }
            Log.i(TAG, "Scheduled context expiry at $epochMs requestCode=$code")
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not schedule exact alarm for context expiry", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, epochMs, pending)
        }
    }

    private suspend fun cancelScheduledExpiry() {
        val prefs = dataStore.data.first()
        val code = prefs[KEY_ALARM_REQUEST_CODE] ?: return
        val intent = Intent(context, RobotContextExpiryReceiver::class.java).apply {
            action = RobotContextExpiryReceiver.ACTION_CLEAR
        }
        val pending = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
        dataStore.edit { it.remove(KEY_ALARM_REQUEST_CODE) }
    }

    private fun computeUntilEpochMs(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun toMinutes(hour: Int?, minute: Int?): Int? {
        if (hour == null || minute == null) return null
        return hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)
    }

    private fun parseProfile(raw: String?): RobotProfile {
        return runCatching { RobotProfile.valueOf(raw ?: "NORMAL") }.getOrDefault(RobotProfile.NORMAL)
    }

    private fun parseNotificationMode(raw: String?): NotificationMode {
        return runCatching { NotificationMode.valueOf(raw ?: "NORMAL") }.getOrDefault(NotificationMode.NORMAL)
    }

    private fun parseProfileParam(raw: String?): RobotProfile {
        return when (raw?.trim()?.lowercase()) {
            "work" -> RobotProfile.WORK
            "call" -> RobotProfile.CALL
            "meeting" -> RobotProfile.MEETING
            "focus" -> RobotProfile.FOCUS
            "normal", null, "" -> RobotProfile.NORMAL
            else -> runCatching { RobotProfile.valueOf(raw.uppercase()) }.getOrDefault(RobotProfile.NORMAL)
        }
    }
}
