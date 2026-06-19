package com.example.mydeskrobot.integration.input.notification

import android.content.pm.PackageManager
import android.util.Log
import com.example.mydeskrobot.data.input.InputSettingsRepository
import com.example.mydeskrobot.integration.input.InputSource
import com.example.mydeskrobot.reasoning.model.InputPriority
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * InputSource for system notifications from other apps.
 */
class NotificationInputSource(
    private val settingsRepository: InputSettingsRepository,
    private val packageManager: PackageManager,
) : InputSource {

    override val id: String = "notification"
    override val priority: InputPriority = InputPriority.DEFERRED
    override val displayName: String = "Notifiche"

    private val recentNotifications = ConcurrentHashMap<String, Long>()
    private val dedupWindowMs = 60_000L

    companion object {
        private const val TAG = "NotificationInputSource"

        private val SYSTEM_BLACKLIST = setOf(
            "android",
            "com.android.systemui",
            "com.android.vending",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.providers.downloads",
        )

        private val SENSITIVE_KEYWORDS = listOf(
            "otp", "codice", "verifica", "password", "pin",
            "banca", "bank", "carta", "credit", "debit",
        )
    }

    override fun isEnabled(): Boolean {
        return runBlocking {
            settingsRepository.isNotificationsEnabled() &&
                settingsRepository.isNotificationAccessGranted()
        }
    }

    override fun normalize(raw: Any): RobotInput? {
        if (raw !is NotificationData) return null

        val appLabel = getAppLabel(raw.packageName)

        return RobotInput.Notification(
            packageName = raw.packageName,
            appLabel = appLabel,
            title = raw.title,
            text = raw.text,
            notificationKey = raw.key,
            timestamp = raw.postTime,
        )
    }

    override fun shouldAccept(input: RobotInput): Boolean {
        if (input !is RobotInput.Notification) return false

        if (SYSTEM_BLACKLIST.contains(input.packageName)) {
            Log.d(TAG, "Rejecting system notification from ${input.packageName}")
            return false
        }

        val isAllowed = runBlocking {
            settingsRepository.isPackageAllowed(input.packageName)
        }
        if (!isAllowed) {
            Log.d(TAG, "Package ${input.packageName} not in whitelist")
            return false
        }

        if (input.title.isNullOrBlank() && input.text.isNullOrBlank()) {
            Log.d(TAG, "Rejecting empty notification")
            return false
        }

        if (isDuplicate(input)) {
            Log.d(TAG, "Rejecting duplicate notification")
            return false
        }

        return true
    }

    override fun toEnvelope(input: RobotInput): SystemInputEnvelope {
        require(input is RobotInput.Notification)

        val sanitizedText = sanitizeForTts(input.text)
        val sanitizedTitle = sanitizeForTts(input.title)

        val formatted = buildString {
            append("[SYSTEM_INPUT: notification]\n")
            append("App: ${input.appLabel}\n")
            sanitizedTitle?.let { append("Titolo: $it\n") }
            sanitizedText?.let { append("Testo: $it\n") }
        }.trimEnd()

        val dedupKey = "notif:${input.packageName}:${input.notificationKey}"

        return SystemInputEnvelope(
            input = input,
            formattedForLlm = formatted,
            dedupKey = dedupKey,
        )
    }

    override fun toDedupKey(input: RobotInput): String {
        require(input is RobotInput.Notification)
        return "notif:${input.packageName}:${input.notificationKey}"
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            InputSettingsRepository.KNOWN_APPS[packageName] ?: packageName
        }
    }

    private fun isDuplicate(input: RobotInput.Notification): Boolean {
        val now = System.currentTimeMillis()
        cleanupOldEntries(now)

        val key = "${input.packageName}:${hashContent(input)}"
        val lastSeen = recentNotifications[key]

        if (lastSeen != null && now - lastSeen < dedupWindowMs) {
            return true
        }

        recentNotifications[key] = now
        return false
    }

    private fun hashContent(input: RobotInput.Notification): Int {
        return (input.title.orEmpty() + input.text.orEmpty()).hashCode()
    }

    private fun cleanupOldEntries(now: Long) {
        recentNotifications.entries.removeIf { now - it.value > dedupWindowMs }
    }

    /**
     * Sanitize text for TTS. Hide sensitive content.
     */
    private fun sanitizeForTts(text: String?): String? {
        if (text.isNullOrBlank()) return null

        val lower = text.lowercase()
        if (SENSITIVE_KEYWORDS.any { lower.contains(it) }) {
            return "[contenuto sensibile nascosto]"
        }

        return text
    }
}

/**
 * Raw notification data passed from the listener service.
 */
data class NotificationData(
    val packageName: String,
    val key: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val isGroupSummary: Boolean,
)
