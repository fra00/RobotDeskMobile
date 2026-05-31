package com.example.mydeskrobot.integration.input.heartbeat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings

/**
 * Manages the repeating alarm for heartbeat ticks.
 * Reads settings from [HeartbeatSettings] to determine interval.
 */
object HeartbeatScheduler {

    private const val TAG = "HeartbeatScheduler"
    private const val REQUEST_CODE = 8001

    fun schedule(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(context)

        val intervalMillis = intervalMinutes.coerceIn(5, 30) * 60_000L
        val triggerAt = System.currentTimeMillis() + intervalMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
                Log.i(TAG, "Scheduled exact heartbeat in $intervalMinutes min")
                return
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent,
        )
        Log.i(TAG, "Scheduled inexact heartbeat in $intervalMinutes min")
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(createPendingIntent(context))
        Log.i(TAG, "Heartbeat alarm cancelled")
    }

    fun rescheduleNext(context: Context, intervalMinutes: Int) {
        schedule(context, intervalMinutes)
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HeartbeatAlarmReceiver::class.java).apply {
            action = HeartbeatAlarmReceiver.ACTION_HEARTBEAT_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
