package com.example.mydeskrobot.domain.time

/**
 * Fascia oraria "notte" (default: dalla mezzanotte alle 6:00).
 * [startHour] incluso, [endHour] escluso (0–23).
 */
data class NightModeConfig(
    val startHour: Int = 0,
    val endHour: Int = 6,
)
