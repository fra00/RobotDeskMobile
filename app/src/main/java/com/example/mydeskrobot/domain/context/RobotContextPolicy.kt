package com.example.mydeskrobot.domain.context

import com.example.mydeskrobot.reasoning.model.NotificationMode
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.reasoning.model.RobotProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure Kotlin rules for robot context (no Android dependencies).
 */
object RobotContextPolicy {

    fun defaultNotificationModeFor(profile: RobotProfile): NotificationMode {
        return when (profile) {
            RobotProfile.NORMAL -> NotificationMode.NORMAL
            RobotProfile.WORK,
            RobotProfile.CALL,
            RobotProfile.MEETING,
            RobotProfile.FOCUS,
            -> NotificationMode.SILENT
        }
    }

    /**
     * Resolves stored state against current time (expiry, daily window).
     * Returns NORMAL if expired or outside window.
     */
    fun resolveEffectiveState(stored: RobotContextState, nowEpochMs: Long = System.currentTimeMillis()): RobotContextState {
        if (stored.isNormal) return RobotContextState.NORMAL

        stored.validUntilEpochMs?.let { until ->
            if (nowEpochMs >= until) return RobotContextState.NORMAL
        }

        val windowStart = stored.windowStartMinutes
        val windowEnd = stored.windowEndMinutes
        if (windowStart != null && windowEnd != null) {
            if (!isInsideWindow(windowStart, windowEnd, nowEpochMs)) {
                return RobotContextState.NORMAL
            }
        }

        return stored
    }

    fun shouldDropNotifications(state: RobotContextState, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val effective = resolveEffectiveState(state, nowEpochMs)
        return effective.notificationMode == NotificationMode.SILENT
    }

    fun isSessionScoped(state: RobotContextState): Boolean {
        return state.sessionOnly && !state.isNormal
    }

    fun buildPromptSection(state: RobotContextState, nowEpochMs: Long = System.currentTimeMillis()): String {
        val effective = resolveEffectiveState(state, nowEpochMs)
        if (effective.isNormal || effective.profile == RobotProfile.NORMAL) {
            return ""
        }

        val profileLabel = when (effective.profile) {
            RobotProfile.WORK -> "WORK (user at work, minimize interruptions)"
            RobotProfile.CALL -> "CALL (user on a call)"
            RobotProfile.MEETING -> "MEETING (user in a meeting)"
            RobotProfile.FOCUS -> "FOCUS (user needs focus)"
            RobotProfile.NORMAL -> "NORMAL"
        }

        val notifLine = if (effective.notificationMode == NotificationMode.SILENT) {
            "Notifications: SILENCED for the robot (do not announce incoming notifications)."
        } else {
            "Notifications: normal."
        }

        val expiryLine = effective.validUntilEpochMs?.let { until ->
            if (until > nowEpochMs) {
                val fmt = SimpleDateFormat("HH:mm", Locale.ITALIAN)
                "Active until: ${fmt.format(Date(until))}."
            } else {
                null
            }
        }.orEmpty()

        val windowLine = if (effective.windowStartMinutes != null && effective.windowEndMinutes != null) {
            "Daily window: ${formatMinutes(effective.windowStartMinutes)} - ${formatMinutes(effective.windowEndMinutes)}."
        } else {
            ""
        }

        return buildString {
            appendLine("ACTIVE ROBOT CONTEXT (desk robot only — does NOT change phone DND):")
            appendLine("- Profile: $profileLabel")
            appendLine("- $notifLine")
            if (expiryLine.isNotBlank()) appendLine("- $expiryLine")
            if (windowLine.isNotBlank()) appendLine("- $windowLine")
            appendLine("- When the user speaks to you, respond normally.")
            appendLine("- Keep replies short and concise unless the user asks for detail.")
        }.trim()
    }

    fun isInsideWindow(startMinutes: Int, endMinutes: Int, nowEpochMs: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            // Overnight window (e.g. 22:00 - 06:00)
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    private fun formatMinutes(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return String.format(Locale.ITALIAN, "%02d:%02d", h, m)
    }
}
