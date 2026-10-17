package com.example.mydeskrobot.integration.proxy.capability.handlers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.R
import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityException
import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityHandler
import org.json.JSONObject

class LocalNotificationCapabilityHandler(
    private val context: Context,
) : PhoneCapabilityHandler {

    override suspend fun handle(params: Map<String, String>): JSONObject {
        val title = params["title"]?.trim().orEmpty().ifBlank { "Promemoria" }
        val text = params["text"]?.trim().orEmpty()
        if (text.isEmpty()) {
            throw PhoneCapabilityException(400, "missing_text")
        }
        val notificationId = params["notificationId"]?.toIntOrNull() ?: DEFAULT_NOTIF_ID

        ensureChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                throw PhoneCapabilityException(403, "notification_permission_denied")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return JSONObject().put("shown", true).put("notificationId", notificationId)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.proxy_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.proxy_reminder_channel_desc)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "mydeskrobot_proxy_reminders"
        private const val DEFAULT_NOTIF_ID = 7_001
    }
}
