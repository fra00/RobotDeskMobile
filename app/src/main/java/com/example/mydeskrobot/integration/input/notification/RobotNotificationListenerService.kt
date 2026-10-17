package com.example.mydeskrobot.integration.input.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.mydeskrobot.data.input.InputSettingsRepository
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent
import com.example.mydeskrobot.domain.proxy.DeviceMode
import com.example.mydeskrobot.integration.input.notification.proxy.NotificationProxyClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * System service that listens for notifications from other apps.
 * ROBOT (no proxies): emit to [SystemInputDispatcher].
 * ROBOT (paired): ignore local (remote only).
 * PROXY: forward over LAN after whitelist.
 */
class RobotNotificationListenerService : NotificationListenerService() {

    private lateinit var inputSource: NotificationInputSource
    private lateinit var settingsRepository: InputSettingsRepository
    private lateinit var modeRepository: DeviceModeRepository
    private lateinit var proxyClient: NotificationProxyClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "NotificationListener"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NotificationListenerService created")
        settingsRepository = InputSettingsRepository(applicationContext)
        modeRepository = DeviceModeRepository(applicationContext)
        inputSource = NotificationInputSource(
            settingsRepository = settingsRepository,
            packageManager = packageManager,
        )
        proxyClient = NotificationProxyClient(applicationContext, modeRepository)
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

        val mode = runBlocking { modeRepository.getDeviceMode() }

        if (mode == DeviceMode.ROBOT) {
            val hasProxies = runBlocking { modeRepository.hasPairedProxies() }
            if (hasProxies) {
                Log.d(TAG, "Ignoring local notification (paired proxy active)")
                return
            }
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

        Log.d(TAG, "Notification received: pkg=${sbn.packageName}, title=$title mode=$mode")

        val robotInput = inputSource.normalize(data) ?: return

        if (!inputSource.shouldAccept(robotInput)) {
            return
        }

        when (mode) {
            DeviceMode.PROXY -> {
                scope.launch {
                    proxyClient.forward(data)
                }
            }
            DeviceMode.ROBOT -> {
                val envelope = inputSource.toEnvelope(robotInput)
                Log.i(TAG, "Dispatching notification input from ${sbn.packageName}")
                SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
            }
        }
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
