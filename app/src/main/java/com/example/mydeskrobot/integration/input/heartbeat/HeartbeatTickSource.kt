package com.example.mydeskrobot.integration.input.heartbeat

/**
 * How a heartbeat tick was started.
 * [ALARM] uses schedule due-check; [VOICE] only relaxes scheduling when no domain is due.
 * Gates (presence, cooldown, window, …) and LLM pipeline are identical.
 */
enum class HeartbeatTickSource {
    ALARM,
    VOICE,
}

sealed class VoiceHeartbeatTriggerResult {
    data class Dispatched(val domainName: String) : VoiceHeartbeatTriggerResult()
    data class GateBlocked(val reason: String) : VoiceHeartbeatTriggerResult()
    data object NoEnabledDomains : VoiceHeartbeatTriggerResult()
}
