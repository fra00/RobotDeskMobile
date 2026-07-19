package com.example.mydeskrobot.domain.predictivity

import com.example.mydeskrobot.domain.proactive.ProactivityConstants
import kotlin.math.min

object HabitSlotConfidence {
    fun confidenceForHitCount(
        hitCount: Int,
        promoteHitCount: Int = ProactivityConstants.PREDICTIVITY_PROMOTE_HIT_COUNT,
    ): Float {
        if (hitCount <= 0) return 0f
        val cap = 0.90f
        val raw = hitCount * (cap / promoteHitCount.toFloat())
        return min(cap, raw)
    }
}
