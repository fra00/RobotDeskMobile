package com.example.mydeskrobot.integration.input.heartbeat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettingsRepository
import kotlinx.coroutines.runBlocking

/**
 * Re-schedules heartbeat alarm after device reboot (if enabled).
 */
class HeartbeatBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != ACTION_QUICKBOOT_POWERON
        ) {
            return
        }

        Log.i(TAG, "Boot completed — checking heartbeat settings")

        val settingsRepo = HeartbeatSettingsRepository(context)
        val settings = runBlocking { settingsRepo.load() }

        if (settings.enabled) {
            Log.i(TAG, "Heartbeat enabled, scheduling with interval ${settings.intervalMinutes} min")
            HeartbeatScheduler.schedule(context, settings.intervalMinutes)
        } else {
            Log.d(TAG, "Heartbeat disabled, not scheduling")
        }
    }

    companion object {
        private const val TAG = "HeartbeatBootRx"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
