package com.example.mydeskrobot.integration.wellness

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.wellness.WellnessDomains
import com.example.mydeskrobot.domain.wellness.WellnessSnapshot
import com.example.mydeskrobot.MainActivity

/**
 * Posts at most one Wellness attention notification per day (Kotlin-owned, not LLM tool).
 */
class WellnessNotifier(
    private val context: Context,
) {
    fun postIfNeeded(snapshot: WellnessSnapshot): Boolean {
        if (!snapshot.needsAttention && !snapshot.notifySuggested) return false
        val entry = snapshot.topAttentionEntry() ?: return false
        if (!hasPermission()) return false

        ensureChannel()
        val title = context.getString(R.string.wellness_notification_title)
        val label = WellnessDomains.DISPLAY_NAMES[entry.id] ?: entry.id
        val body = context.getString(R.string.wellness_notification_body, label, entry.summaryIt)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_WELLNESS_SUMMARY, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wellness_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.wellness_notification_channel_desc)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_OPEN_WELLNESS_SUMMARY = "open_wellness_summary"
        private const val CHANNEL_ID = "mydeskrobot_wellness"
        private const val NOTIFICATION_ID = 7101
        private const val REQUEST_CODE = 7101
    }
}
