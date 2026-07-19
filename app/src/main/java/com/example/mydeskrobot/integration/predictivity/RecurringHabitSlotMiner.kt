package com.example.mydeskrobot.integration.predictivity

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.predictivity.HabitSlotRepository
import com.example.mydeskrobot.data.predictivity.PredictivityMiningStore
import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.predictivity.HabitSlotKey
import com.example.mydeskrobot.domain.predictivity.HabitPendingMiner
import com.example.mydeskrobot.domain.predictivity.MiningResult
import com.example.mydeskrobot.domain.proactive.ProactivityConstants

class RecurringHabitSlotMiner(
    private val activityLogRepository: ActivityLogRepository,
    private val habitSlotRepository: HabitSlotRepository,
    private val miningRepository: PredictivityMiningStore,
    private val labelNormalizer: HabitLabelNormalizer,
) : HabitPendingMiner {
    override suspend fun minePendingDays(): MiningResult {
        val lastMined = miningRepository.getLastMinedDayKey()
        val pendingDays = activityLogRepository.pendingMiningDayKeys(lastMined)
        if (pendingDays.isEmpty()) {
            return MiningResult(daysProcessed = 0, slotsUpdated = 0, lastMinedDayKey = lastMined)
        }

        val rawLabels = linkedSetOf<String>()
        pendingDays.forEach { dayKey ->
            activityLogRepository.getConfirmedPhysicalEvents(dayKey).forEach { episode ->
                rawLabels.add(episode.label)
            }
        }

        val labelMap = labelNormalizer.normalize(rawLabels.toList())
        var slotsUpdated = 0
        var lastProcessedDay: String? = lastMined

        for (dayKey in pendingDays) {
            val hitSlotsToday = mutableSetOf<String>()
            val episodes = activityLogRepository.getConfirmedPhysicalEvents(dayKey)
            for (episode in episodes) {
                val canonical = labelMap[episode.label] ?: continue
                val bucket = HabitSlotKey.timeBucketMinutes(episode.timestampMs)
                val slotKey = HabitSlotKey.buildSlotKey(canonical, bucket)
                if (!hitSlotsToday.add(slotKey)) continue

                val episodeMinutes = HabitSlotKey.minutesSinceMidnight(episode.timestampMs)
                val existing = habitSlotRepository.findBySlotKey(slotKey)
                val displayLabel = existing?.displayLabel
                    ?: HabitLabelNormalizer.displayLabelFromCanonical(canonical)
                val updated = (existing ?: HabitSlot(
                    slotKey = slotKey,
                    canonicalLabel = canonical,
                    displayLabel = displayLabel,
                    typicalTimeMinutes = episodeMinutes,
                    timeToleranceMinutes = ProactivityConstants.PREDICTIVITY_TIME_TOLERANCE_MINUTES,
                )).withHit(
                    episodeTimeMinutes = episodeMinutes,
                    dayKey = dayKey,
                    rawLabel = episode.label,
                )
                habitSlotRepository.upsert(updated)
                slotsUpdated++
            }
            lastProcessedDay = dayKey
            miningRepository.setLastMinedDayKey(dayKey)
        }

        return MiningResult(
            daysProcessed = pendingDays.size,
            slotsUpdated = slotsUpdated,
            lastMinedDayKey = lastProcessedDay,
        )
    }
}
