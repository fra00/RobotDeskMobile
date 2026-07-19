package com.example.mydeskrobot.integration.predictivity

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.data.proactive.ProactivitySettings
import com.example.mydeskrobot.data.predictivity.HabitSlotRepository
import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.predictivity.HabitSlotKey
import com.example.mydeskrobot.domain.proactive.GateDecision
import com.example.mydeskrobot.domain.proactive.ProactiveSpeakContext
import com.example.mydeskrobot.domain.proactive.ProactiveSpeakGate
import com.example.mydeskrobot.domain.proactive.UserPresencePolicy
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.integration.presence.BodyLocateService
import com.example.mydeskrobot.reasoning.model.RobotContextState
import kotlin.math.abs

data class DeviationWatchContext(
    val heartbeatSettings: HeartbeatSettings,
    val proactivitySettings: ProactivitySettings,
    val workingMemory: WorkingMemory,
    val robotContext: RobotContextState?,
    val bodyConfigured: Boolean,
    val bodyReachable: Boolean,
    val micSessionActive: Boolean,
)

class DeviationWatcher(
    private val habitSlotRepository: HabitSlotRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val bodyLocateService: BodyLocateService,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun findCandidate(context: DeviationWatchContext): HabitSlot? {
        if (!context.micSessionActive) return null
        if (!context.heartbeatSettings.enabled) return null
        if (!context.proactivitySettings.predictivityEnabled) return null

        val speakGate = ProactiveSpeakGate.canSpeak(
            ProactiveSpeakContext(
                heartbeatSettings = context.heartbeatSettings,
                workingMemory = context.workingMemory,
                robotContext = context.robotContext,
                nowMs = nowMillis(),
            ),
        )
        if (speakGate !is GateDecision.Proceed) return null

        val now = nowMillis()
        val todayKey = ActivityLogRepository.dayKeyFor(now)
        val currentMinutes = HabitSlotKey.minutesSinceMidnight(now)

        for (slot in habitSlotRepository.listEligibleForDeviation()) {
            if (context.workingMemory.deviationAskedSlotKeysToday.contains(slot.slotKey)) continue
            if (context.workingMemory.deviationSuppressedSlotKeysToday.contains(slot.slotKey)) continue
            if (!isInDeviationWindow(currentMinutes, slot)) continue
            if (activityLogRepository.hasMatchingEpisodeToday(
                    slot = slot,
                    todayKey = todayKey,
                    toleranceMinutes = slot.timeToleranceMinutes,
                )
            ) {
                continue
            }

            val present = UserPresencePolicy.predictivityPresentEnough(
                lastUserTurnMs = context.workingMemory.lastUserTurnMillis,
                bodyConfigured = context.bodyConfigured,
                bodyReachable = context.bodyReachable,
                locateUser = { bodyLocateService.locateUserNow() },
                now = now,
            )
            if (!present) continue
            return slot
        }
        return null
    }

    private fun isInDeviationWindow(currentMinutes: Int, slot: HabitSlot): Boolean {
        val delta = abs(currentMinutes - slot.typicalTimeMinutes)
        return delta <= slot.timeToleranceMinutes
    }
}
