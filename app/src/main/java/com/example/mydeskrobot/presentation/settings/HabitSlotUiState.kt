package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.predictivity.HabitSlotEligibility
import com.example.mydeskrobot.domain.proactive.ProactivityConstants
import kotlin.math.ceil

data class HabitSlotUiState(
    val slotKey: String,
    val displayLabel: String,
    val typicalTimeLabel: String,
    val windowLabel: String,
    val hitsLabel: String,
    val statusLabel: String,
    val eligible: Boolean,
)

object HabitSlotUiMapper {
    fun fromDomain(slot: HabitSlot): HabitSlotUiState {
        val eligible = HabitSlotEligibility.isEligibleForDeviation(slot)
        val typical = formatMinutesSinceMidnight(slot.typicalTimeMinutes)
        val start = formatMinutesSinceMidnight(
            (slot.typicalTimeMinutes - slot.timeToleranceMinutes).mod(24 * 60),
        )
        val end = formatMinutesSinceMidnight(
            (slot.typicalTimeMinutes + slot.timeToleranceMinutes).mod(24 * 60),
        )
        val hitsNeeded = hitsNeededForEligibility()
        return HabitSlotUiState(
            slotKey = slot.slotKey,
            displayLabel = slot.displayLabel.ifBlank { slot.canonicalLabel },
            typicalTimeLabel = typical,
            windowLabel = "$start–$end (±${slot.timeToleranceMinutes} min)",
            hitsLabel = "${slot.hitCount} giorni · confidenza ${(slot.confidence * 100).toInt()}%",
            statusLabel = if (eligible) {
                "Pronto a chiedere se salti"
            } else {
                "In apprendimento (${slot.hitCount}/~$hitsNeeded giorni)"
            },
            eligible = eligible,
        )
    }

    fun formatMinutesSinceMidnight(minutes: Int): String {
        val normalized = minutes.mod(24 * 60)
        val hours = normalized / 60
        val mins = normalized % 60
        return "%02d:%02d".format(hours, mins)
    }

    fun hitsNeededForEligibility(
        minConfidence: Float = ProactivityConstants.PREDICTIVITY_MIN_CONFIDENCE,
        promoteHitCount: Int = ProactivityConstants.PREDICTIVITY_PROMOTE_HIT_COUNT,
    ): Int {
        // confidence = hitCount * (0.90 / promoteHitCount); need >= minConfidence
        val perHit = 0.90f / promoteHitCount.toFloat()
        return ceil(minConfidence / perHit).toInt().coerceAtLeast(
            ProactivityConstants.PREDICTIVITY_MIN_HIT_COUNT,
        )
    }
}
