package com.example.mydeskrobot.domain.vision

/**
 * Result of a presence check (person at desk or not).
 * Does NOT infer activity (work vs play).
 */
enum class PresenceStatus {
    PRESENT,
    ABSENT,
    UNCERTAIN,
}

data class PresenceDetectionResult(
    val status: PresenceStatus,
    val confidence: Float,
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in [0, 1]" }
    }
}
