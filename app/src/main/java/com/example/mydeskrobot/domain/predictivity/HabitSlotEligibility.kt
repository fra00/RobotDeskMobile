package com.example.mydeskrobot.domain.predictivity

import com.example.mydeskrobot.domain.proactive.ProactivityConstants

object HabitSlotEligibility {
    fun isEligibleForDeviation(
        slot: HabitSlot,
        minHitCount: Int = ProactivityConstants.PREDICTIVITY_MIN_HIT_COUNT,
        minConfidence: Float = ProactivityConstants.PREDICTIVITY_MIN_CONFIDENCE,
    ): Boolean =
        slot.hitCount >= minHitCount && slot.confidence >= minConfidence
}
