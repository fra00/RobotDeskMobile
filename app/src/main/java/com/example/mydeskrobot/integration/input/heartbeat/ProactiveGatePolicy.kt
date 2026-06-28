package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.data.presence.DeskPresenceSettingsRepository
import com.example.mydeskrobot.data.presence.DeskPresenceStateStore
import com.example.mydeskrobot.data.hotword.VoiceSessionState
import com.example.mydeskrobot.domain.awareness.UserAwarenessState
import com.example.mydeskrobot.domain.awareness.UserStateTracker
import com.example.mydeskrobot.domain.context.RobotContextPolicy
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.domain.presence.DeskPresenceGate
import java.util.Calendar

data class ProactiveGateContext(
    val heartbeatSettings: HeartbeatSettings,
    val workingMemory: WorkingMemory?,
    val userAwareness: UserAwarenessState?,
    val robotContext: RobotContextState?,
    val isNightMode: Boolean,
    val deskPresenceMonitorEnabled: Boolean,
)

class ProactiveGatePolicy(
    private val deskPresenceSettingsRepository: DeskPresenceSettingsRepository,
) {
    suspend fun shouldRunTick(context: ProactiveGateContext): GateDecision {
        val settings = context.heartbeatSettings
        if (!settings.enabled) return GateDecision.Skip("heartbeat disabled")
        if (!VoiceSessionState.isActive) return GateDecision.Skip("mic session inactive")
        if (!isWithinActiveWindow(settings)) {
            return GateDecision.Skip("outside active window")
        }

        val deskSettings = deskPresenceSettingsRepository.load()
        val occupancy = DeskPresenceStateStore.current()
        if (!DeskPresenceGate.allowsProactiveInteraction(
                occupancy = occupancy,
                lastInteractionMillis = settings.lastInteractionMillis,
                monitorEnabled = deskSettings.enabled,
            )
        ) {
            return GateDecision.Skip("desk absent (ML Kit)")
        }

        if (context.robotContext != null &&
            RobotContextPolicy.shouldSuppressNotificationTts(context.robotContext)
        ) {
            return GateDecision.Skip("robot context silent")
        }

        if (context.isNightMode) {
            return GateDecision.Skip("night mode")
        }

        val wm = context.workingMemory
        if (wm != null && wm.proactiveSpeaksToday >= MAX_PROACTIVE_SPEAKS_PER_DAY) {
            return GateDecision.Skip("daily proactive cap")
        }

        val minutesSinceLast = wm?.minutesSinceLastProactiveSpeak()
        if (minutesSinceLast != null && minutesSinceLast < MIN_MINUTES_BETWEEN_PROACTIVE) {
            return GateDecision.Skip("proactive cooldown")
        }

        return GateDecision.Proceed
    }

    fun shouldSpeak(
        speakConfidence: Float?,
        finalText: String,
        settings: HeartbeatSettings,
        userAwareness: UserAwarenessState?,
    ): Boolean {
        if (finalText.isBlank()) return false
        val confidence = speakConfidence ?: return false
        val modifier = userAwareness?.let { UserStateTracker.interventionConfidenceModifier(it) } ?: 1f
        val threshold = settings.proactiveThreshold / modifier
        return confidence >= threshold
    }

    private fun isWithinActiveWindow(settings: HeartbeatSettings): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        return if (settings.startHour <= settings.endHour) {
            currentHour in settings.startHour until settings.endHour
        } else {
            currentHour >= settings.startHour || currentHour < settings.endHour
        }
    }

    companion object {
        const val MAX_PROACTIVE_SPEAKS_PER_DAY = 3
        const val MIN_MINUTES_BETWEEN_PROACTIVE = 20L
    }
}

sealed interface GateDecision {
    data object Proceed : GateDecision
    data class Skip(val reason: String) : GateDecision
}
