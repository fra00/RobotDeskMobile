package com.example.mydeskrobot.domain.proactive

/**
 * Shared proactivity thresholds (predictivity + future wellness).
 * SSOT defaults: docs/PROACTIVE_ARCHITECTURE.md
 */
object ProactivityConstants {
    const val PREDICTIVITY_PRESENCE_MINUTES = 10

    const val TIME_BUCKET_MINUTES = 30

    const val PREDICTIVITY_PROMOTE_HIT_COUNT = 7
    const val PREDICTIVITY_MIN_HIT_COUNT = 3
    const val PREDICTIVITY_MIN_CONFIDENCE = 0.70f
    const val PREDICTIVITY_TIME_TOLERANCE_MINUTES = 45

    const val WELLNESS_ANCHOR_MINUTES = 60
    /** Buffer after last user turn so wellness does not overlap an ongoing dialog. */
    const val WELLNESS_IDLE_MINUTES = 5
    /**
     * Presence fallback: recent interaction when body locate fails / no body.
     * Must be ≥ [WELLNESS_IDLE_MINUTES] so a non-empty start window exists after the idle buffer.
     */
    const val WELLNESS_PRESENCE_MINUTES = 15

    const val PATTERN_TTL_DAYS = 30

    const val HABIT_SLOT_VALUE_PREFIX = "HABIT_SLOT:v1:"
    const val HABIT_SLOT_EXTERNAL_REF_PREFIX = "habit_slot:"
}
