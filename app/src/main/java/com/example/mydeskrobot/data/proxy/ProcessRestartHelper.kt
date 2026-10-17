package com.example.mydeskrobot.data.proxy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.mydeskrobot.MainActivity

/**
 * Restarts the process so PROXY/ROBOT bootstrap can take effect cleanly.
 */
object ProcessRestartHelper {

    fun restartApp(context: Context) {
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getActivity(context, 1001, launch, flags)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + 400L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pending)
        } else {
            alarm.setExact(AlarmManager.RTC, triggerAt, pending)
        }
        Runtime.getRuntime().exit(0)
    }
}
