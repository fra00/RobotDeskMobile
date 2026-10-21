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
    /** Calendar day key for "today" (yyyy-MM-dd). */
    val todayDayKey: String = "",
    /** Latest snapshot compute time, if any. */
    val lastSnapshotComputedAtMs: Long? = null,
    /** Day key of latest snapshot, if any. */
    val lastSnapshotDayKey: String? = null,
    /** On-demand ask («come sto?») — force DOMAIN_SCORE now (still skips redoing visual). */
    val forceDomainScore: Boolean = false,
)

/**
 * Scheduling for Wellness **compute** (v2).
 *
 * Gates: enabled, LLM idle, not night, translation off, daily anchor.
 * DOMAIN_SCORE: first of day, or snapshot older than [ProactivitySettings.wellnessRescoreIntervalMinutes],
 * or [WellnessWatchContext.forceDomainScore] (on-demand).
 * VISUAL_ORDER: still once/day when body + order domain available.
 * **Not** gated on mic / hotword session (compute ≠ communicate).
 */
class WellnessWatcher(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun nextPhase(context: WellnessWatchContext): WellnessPhase? {
        if (!baseSchedulingGates(context)) return null
        if (context.enabledDomainIds.isEmpty()) return null

        val orderEnabled = WellnessDomains.ORDER in context.enabledDomainIds
        val bodyReady = context.bodyConfigured && context.bodyReachable
        // On-demand ask skips visual — score from text/log only; photos stay once/day via scheduler.
        if (
            !context.forceDomainScore &&
            orderEnabled &&
            bodyReady &&
            !context.workingMemory.wellnessVisualDoneToday
        ) {
            return WellnessPhase.VISUAL_ORDER
        }
        if (needsDomainScore(context)) {
            return WellnessPhase.DOMAIN_SCORE
        }
        return null
    }

    fun needsDomainScore(context: WellnessWatchContext): Boolean {
        if (context.forceDomainScore) return true
        val computedAt = context.lastSnapshotComputedAtMs
        val snapDay = context.lastSnapshotDayKey
        val today = context.todayDayKey
        if (computedAt == null || snapDay.isNullOrBlank() || today.isBlank() || snapDay != today) {
            return true
        }
        val ageMinutes = (nowMillis() - computedAt) / 60_000L
        val interval = context.proactivitySettings.wellnessRescoreIntervalMinutes.coerceIn(60, 720)
        return ageMinutes >= interval
    }

    private fun baseSchedulingGates(context: WellnessWatchContext): Boolean {
        if (!context.proactivitySettings.wellnessEnabled) return false
        if (context.isNightMode) return false
        if (context.llmBusy) return false
        if (context.translationModeActive) return false

        // On-demand may skip waiting for the cold-start anchor.
        if (context.forceDomainScore) return true

        // Anchor = first day activity timestamp (hotword-on or app session open).
        val anchor = context.workingMemory.firstHotwordOnTodayMs ?: return false
        val now = nowMillis()
        val anchorElapsedMinutes = (now - anchor) / 60_000L
        if (anchorElapsedMinutes < context.proactivitySettings.wellnessAnchorMinutes) return false

        return true
    }
}
