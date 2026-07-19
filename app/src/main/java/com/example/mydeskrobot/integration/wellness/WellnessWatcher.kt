package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.data.proactive.ProactivitySettings
import com.example.mydeskrobot.domain.context.RobotContextPolicy
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.domain.wellness.WellnessDomains
import com.example.mydeskrobot.domain.wellness.WellnessPhase
import com.example.mydeskrobot.reasoning.model.RobotContextState

data class WellnessWatchContext(
    val heartbeatSettings: HeartbeatSettings,
    val proactivitySettings: ProactivitySettings,
    val workingMemory: WorkingMemory,
    val robotContext: RobotContextState?,
    val micSessionActive: Boolean,
    val bodyConfigured: Boolean,
    val bodyReachable: Boolean,
    val llmBusy: Boolean,
    val isNightMode: Boolean = false,
    val enabledDomainIds: Set<String> = WellnessDomains.ALL,
)

class WellnessWatcher(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /**
     * Returns the next wellness phase to run, or null if no check is due.
     */
    fun nextPhase(context: WellnessWatchContext): WellnessPhase? {
        if (!baseSchedulingGates(context)) return null
        if (context.enabledDomainIds.isEmpty()) return null

        val orderEnabled = WellnessDomains.ORDER in context.enabledDomainIds
        val bodyReady = context.bodyConfigured && context.bodyReachable
        if (orderEnabled && bodyReady && !context.workingMemory.wellnessVisualDoneToday) {
            return WellnessPhase.VISUAL_ORDER
        }
        if (!context.workingMemory.wellnessCheckDoneToday) {
            return WellnessPhase.DOMAIN_SCORE
        }
        return null
    }

    private fun baseSchedulingGates(context: WellnessWatchContext): Boolean {
        if (!context.micSessionActive) return false
        if (!context.heartbeatSettings.enabled) return false
        if (!context.proactivitySettings.wellnessEnabled) return false
        if (context.isNightMode) return false
        if (context.llmBusy) return false

        if (context.robotContext != null &&
            RobotContextPolicy.shouldSuppressNotificationTts(context.robotContext, nowMillis())
        ) {
            return false
        }

        val anchor = context.workingMemory.firstHotwordOnTodayMs ?: return false
        val now = nowMillis()
        val anchorElapsedMinutes = (now - anchor) / 60_000L
        if (anchorElapsedMinutes < context.proactivitySettings.wellnessAnchorMinutes) return false

        val idleMinutes = context.workingMemory.minutesSinceLastUserTurn(now)
        if (idleMinutes == null || idleMinutes < context.proactivitySettings.wellnessIdleMinutes) {
            return false
        }

        return true
    }
}
