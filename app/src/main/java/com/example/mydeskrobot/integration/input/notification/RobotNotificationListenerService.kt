package com.example.mydeskrobot.integration.input.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.mydeskrobot.data.input.InputSettingsRepository
import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent

/**
 * System service that listens for notifications from other apps.
 * Requires user to grant notification access in system settings.
 */
class RobotNotificationListenerService : NotificationListenerService() {

    private lateinit var inputSource: NotificationInputSource
    private lateinit var settingsRepository: InputSettingsRepository

    companion object {
        private const val TAG = "NotificationListener"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NotificationListenerService created")
        settingsRepository = InputSettingsRepository(applicationContext)
        inputSource = NotificationInputSource(
            settingsRepository = settingsRepository,
            packageManager = packageManager,
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        if (sbn.packageName == packageName) {
            return
        }

        val notification = sbn.notification ?: return

        if (isGroupSummary(notification)) {
            Log.d(TAG, "Ignoring group summary from ${sbn.packageName}")
            return
        }

        if (!inputSource.isEnabled()) {
            Log.d(TAG, "Notification input source is disabled")
            return
        }

        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val data = NotificationData(
            packageName = sbn.packageName,
            key = sbn.key,
            title = title,
            text = text,
            postTime = sbn.postTime,
            isGroupSummary = isGroupSummary(notification),
        )

        Log.d(TAG, "Notification received: pkg=${sbn.packageName}, title=$title")

        val robotInput = inputSource.normalize(data) ?: return

        if (!inputSource.shouldAccept(robotInput)) {
            return
        }

        val envelope = inputSource.toEnvelope(robotInput)
        Log.i(TAG, "Dispatching notification input from ${sbn.packageName}")
        SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not used for now
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListenerService connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "NotificationListenerService disconnected")
    }

    private fun isGroupSummary(notification: Notification): Boolean {
        return notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    }
}
