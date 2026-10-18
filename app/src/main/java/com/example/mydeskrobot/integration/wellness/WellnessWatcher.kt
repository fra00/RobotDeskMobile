package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.data.proactive.ProactivitySettings
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.domain.wellness.WellnessDomains
import com.example.mydeskrobot.domain.wellness.WellnessPhase
import com.example.mydeskrobot.reasoning.model.RobotContextState

data class WellnessWatchContext(
    val proactivitySettings: ProactivitySettings,
    val workingMemory: WorkingMemory,
    val robotContext: RobotContextState?,
    val micSessionActive: Boolean,
    val bodyConfigured: Boolean,
    val bodyReachable: Boolean,
    val llmBusy: Boolean,
    val isNightMode: Boolean = false,
    val translationModeActive: Boolean = false,
    val enabledDomainIds: Set<String> = WellnessDomains.ALL,
    /** Unused for v2 compute; kept for call-site compatibility. */
    val locateUser: suspend () -> Boolean = { false },
)

/**
 * Scheduling for Wellness **compute** (v2).
 *
 * Gates: enabled, LLM idle, not night, translation off, daily anchor, once/day.
 * Dropped vs v1: idle-after-dialog, user presence / locateUser, SILENT robot context
 * (SILENT still blocks **notification**, not compute).
 */
class WellnessWatcher(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun nextPhase(context: WellnessWatchContext): WellnessPhase? {
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
        if (!context.proactivitySettings.wellnessEnabled) return false
        if (context.isNightMode) return false
        if (context.llmBusy) return false
        if (context.translationModeActive) return false

        val anchor = context.workingMemory.firstHotwordOnTodayMs ?: return false
        val now = nowMillis()
        val anchorElapsedMinutes = (now - anchor) / 60_000L
        if (anchorElapsedMinutes < context.proactivitySettings.wellnessAnchorMinutes) return false

        return true
    }
}
