package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.data.presence.DeskPresenceSettingsRepository
import com.example.mydeskrobot.data.presence.DeskPresenceStateStore
import com.example.mydeskrobot.data.hotword.VoiceSessionState
import com.example.mydeskrobot.domain.context.RobotContextPolicy
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.domain.presence.DeskPresenceGate
import com.example.mydeskrobot.domain.proactive.GateDecision
import com.example.mydeskrobot.domain.proactive.ProactiveSpeakContext
import com.example.mydeskrobot.domain.proactive.ProactiveSpeakGate
import java.util.Calendar

data class ProactiveGateContext(
    val heartbeatSettings: HeartbeatSettings,
    val workingMemory: WorkingMemory?,
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

        return ProactiveSpeakGate.canSpeak(
            ProactiveSpeakContext(
                heartbeatSettings = settings,
                workingMemory = context.workingMemory,
                robotContext = context.robotContext,
            ),
        )
    }

    fun shouldSpeak(
        speakConfidence: Float?,
        finalText: String,
        settings: HeartbeatSettings,
    ): Boolean {
        if (finalText.isBlank()) return false
        val confidence = speakConfidence ?: return false
        return confidence >= settings.proactiveThreshold
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

