package com.example.mydeskrobot.domain.proactive

import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.domain.context.RobotContextPolicy
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.integration.input.heartbeat.ProactiveGatePolicy
import com.example.mydeskrobot.reasoning.model.RobotContextState
import java.util.Calendar

/**
 * Speak-side proactive gates (cap, cooldown, robot context, active window).
 * Reusable by predictivity deviation and future wellness.
 */
data class ProactiveSpeakContext(
    val heartbeatSettings: HeartbeatSettings,
    val workingMemory: WorkingMemory?,
    val robotContext: RobotContextState?,
    val nowMs: Long = System.currentTimeMillis(),
)

object ProactiveSpeakGate {

    fun canSpeak(context: ProactiveSpeakContext): GateDecision {
        val settings = context.heartbeatSettings
        if (!settings.enabled) return GateDecision.Skip("proactivity disabled")

        if (context.robotContext != null &&
            RobotContextPolicy.shouldSuppressNotificationTts(context.robotContext, context.nowMs)
        ) {
            return GateDecision.Skip("robot context silent")
        }

        if (!isWithinActiveWindow(settings, context.nowMs)) {
            return GateDecision.Skip("outside active window")
        }

        val wm = context.workingMemory
        if (wm != null && wm.proactiveSpeaksToday >= ProactiveGatePolicy.MAX_PROACTIVE_SPEAKS_PER_DAY) {
            return GateDecision.Skip("daily proactive cap")
        }

        val minutesSinceLast = wm?.minutesSinceLastProactiveSpeak(context.nowMs)
        if (minutesSinceLast != null && minutesSinceLast < ProactiveGatePolicy.MIN_MINUTES_BETWEEN_PROACTIVE) {
            return GateDecision.Skip("proactive cooldown")
        }

        return GateDecision.Proceed
    }

    private fun isWithinActiveWindow(settings: HeartbeatSettings, nowMs: Long): Boolean {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        return if (settings.startHour <= settings.endHour) {
            currentHour in settings.startHour until settings.endHour
        } else {
            currentHour >= settings.startHour || currentHour < settings.endHour
        }
    }
}
