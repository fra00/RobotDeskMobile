package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.domain.proactive.ProactiveSpeakGate

/**
 * Shared proactive speak caps (used by [ProactiveSpeakGate]).
 * Former alarm/tick gates were removed with HeartbeatOrchestrator.
 */
object ProactiveGatePolicy {
    const val MAX_PROACTIVE_SPEAKS_PER_DAY = 3
    const val MIN_MINUTES_BETWEEN_PROACTIVE = 20L

    fun shouldSpeak(
        speakConfidence: Float?,
        finalText: String,
        settings: HeartbeatSettings,
    ): Boolean {
        if (finalText.isBlank()) return false
        val confidence = speakConfidence ?: return false
        return confidence >= settings.proactiveThreshold
    }
}
