package com.example.mydeskrobot.domain.predictivity

/**
 * Recurring habit slot mined from Log Day episodes.
 */
data class HabitSlot(
    val slotKey: String,
    val canonicalLabel: String,
    val displayLabel: String,
    val typicalTimeMinutes: Int,
    val timeToleranceMinutes: Int = 45,
    val hitCount: Int = 0,
    val lastHitDayKey: String? = null,
    val confidence: Float = 0f,
    val rawLabels: Set<String> = emptySet(),
    val source: String = "activity_log_miner",
) {
    fun withHit(
        episodeTimeMinutes: Int,
        dayKey: String,
        rawLabel: String,
    ): HabitSlot {
        val newHitCount = hitCount + 1
        val newTypical = if (hitCount == 0) {
            episodeTimeMinutes
        } else {
            ((typicalTimeMinutes * hitCount) + episodeTimeMinutes) / newHitCount
        }
        return copy(
            hitCount = newHitCount,
            lastHitDayKey = dayKey,
            typicalTimeMinutes = newTypical,
            confidence = HabitSlotConfidence.confidenceForHitCount(newHitCount),
            rawLabels = rawLabels + rawLabel.trim(),
        )
    }
}

data class MiningResult(
    val daysProcessed: Int,
    val slotsUpdated: Int,
    val lastMinedDayKey: String?,
)
