package com.example.mydeskrobot.domain.wellness

enum class WellnessPhase {
    /** Silent body scan + room order photo (ESP32 only). */
    VISUAL_ORDER,
    /** Score care domains and optionally speak once. */
    DOMAIN_SCORE,
}
