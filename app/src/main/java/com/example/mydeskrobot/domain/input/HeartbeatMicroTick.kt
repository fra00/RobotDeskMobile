package com.example.mydeskrobot.domain.input

/**
 * Payload for a heartbeat MICRO tick (zero LLM).
 */
data class HeartbeatMicroTick(
    val idleMinutes: Long,
    val moodLabel: String?,
    val suggestBodyLookAround: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)
