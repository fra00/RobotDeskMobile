package com.example.mydeskrobot.integration.predictivity

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.predictivity.HabitSlotRepository
import com.example.mydeskrobot.data.predictivity.PredictivityMiningStore
import com.example.mydeskrobot.domain.activitylog.RobotSelfActivityFilter
import com.example.mydeskrobot.domain.predictivity.HabitCanonicalPolicy
import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.predictivity.HabitSlotKey
import com.example.mydeskrobot.domain.predictivity.HabitSlotMergePolicy
import com.example.mydeskrobot.domain.predictivity.HabitPendingMiner
import com.example.mydeskrobot.domain.predictivity.MiningResult
import com.example.mydeskrobot.domain.proactive.ProactivityConstants

/**
 * Hybrid habit miner: LLM normalizes labels (with existing-slot context);
 * Kotlin owns hit counts, meal-by-time refine, and same-canonical time-window merge.
 */
class RecurringHabitSlotMiner(
    private val activityLogRepository: ActivityLogRepository,
    private val habitSlotRepository: HabitSlotRepository,
    private val miningRepository: PredictivityMiningStore,
    private val labelNormalizer: HabitLabelNormalizer,
) : HabitPendingMiner {
    override suspend fun minePendingDays(): MiningResult {
        habitSlotRepository.deleteRobotSelfSlots()

        val lastMined = miningRepository.getLastMinedDayKey()
        val pendingDays = activityLogRepository.pendingMiningDayKeys(lastMined)
        val existingBefore = habitSlotRepository.listAll()

        val rawLabels = linkedSetOf<String>()
        pendingDays.forEach { dayKey ->
            activityLogRepository.getConfirmedPhysicalEvents(dayKey).forEach { episode ->
                if (!RobotSelfActivityFilter.isRobotSelfLabel(episode.label)) {
                    rawLabels.add(episode.label)
                }
            }
        }
        existingBefore.forEach { slot ->
            rawLabels.add(slot.displayLabel)
            rawLabels.add(slot.canonicalLabel.replace('_', ' '))
            rawLabels.addAll(slot.rawLabels)
        }

        val labelMap = if (rawLabels.isEmpty()) {
            emptyMap()
        } else {
            labelNormalizer.normalize(
                distinctRawLabels = rawLabels.toList(),
                existingHabits = existingBefore.map {
                    HabitNormalizeExisting(
                        canonicalLabel = it.canonicalLabel,
                        displayLabel = it.displayLabel,
                        typicalTimeMinutes = it.typicalTimeMinutes,
                    )
                },
            )
        }

        consolidateExistingSlots(labelMap)

        if (pendingDays.isEmpty()) {
            return MiningResult(daysProcessed = 0, slotsUpdated = 0, lastMinedDayKey = lastMined)
        }

        var slotsUpdated = 0
        var lastProcessedDay: String? = lastMined
        // Working set so same-run merges see newly upserted slots.
        val workingSlots = habitSlotRepository.listAll().toMutableList()

        for (dayKey in pendingDays) {
            val hitSlotsToday = mutableSetOf<String>()
            val episodes = activityLogRepository.getConfirmedPhysicalEvents(dayKey)
            for (episode in episodes) {
                if (RobotSelfActivityFilter.isRobotSelfLabel(episode.label)) continue
                val mapped = labelMap[episode.label] ?: continue
                if (mapped == "ignore_robot" ||
                    RobotSelfActivityFilter.isRobotSelfLabel(mapped.replace('_', ' '))
                ) {
                    continue
                }

                val episodeMinutes = HabitSlotKey.minutesSinceMidnight(episode.timestampMs)
                val canonical = HabitCanonicalPolicy.refineMealByTime(
                    HabitCanonicalPolicy.applyLocalAliases(mapped),
                    episodeMinutes,
                )
                if (RobotSelfActivityFilter.isRobotSelfLabel(canonical.replace('_', ' '))) continue

                val compatible = HabitSlotMergePolicy.findCompatible(
                    candidates = workingSlots.filter { it.canonicalLabel == canonical },
                    episodeMinutes = episodeMinutes,
                )
                val slotKey = compatible?.slotKey
                    ?: HabitSlotKey.buildSlotKey(
                        canonical,
                        HabitSlotKey.timeBucketMinutes(episode.timestampMs),
                    )
                if (!hitSlotsToday.add(slotKey)) continue

                val existing = workingSlots.find { it.slotKey == slotKey }
                val displayLabel = existing?.displayLabel
                    ?: HabitLabelNormalizer.displayLabelFromCanonical(canonical)
                val rawLabelsForSlot = (existing?.rawLabels.orEmpty()) + episode.label
                if (
                    RobotSelfActivityFilter.isRobotSelfHabit(
                        displayLabel = displayLabel,
                        canonicalLabel = canonical,
                        rawLabels = rawLabelsForSlot,
                    )
                ) {
                    continue
                }

                val base = existing ?: HabitSlot(
                    slotKey = slotKey,
                    canonicalLabel = canonical,
                    displayLabel = displayLabel,
                    typicalTimeMinutes = episodeMinutes,
                    timeToleranceMinutes = ProactivityConstants.PREDICTIVITY_TIME_TOLERANCE_MINUTES,
                )
                val updated = base.withHit(
                    episodeTimeMinutes = episodeMinutes,
                    dayKey = dayKey,
                    rawLabel = episode.label,
                )
                // If merge target used an old key but typical moved buckets, rewrite key.
                val rewritten = rewriteSlotKeyIfNeeded(updated)
                if (existing != null && existing.slotKey != rewritten.slotKey) {
                    habitSlotRepository.deleteBySlotKey(existing.slotKey)
                    workingSlots.removeAll { it.slotKey == existing.slotKey }
                }
                habitSlotRepository.upsert(rewritten)
                workingSlots.removeAll { it.slotKey == rewritten.slotKey || it.slotKey == slotKey }
                workingSlots.add(rewritten)
                slotsUpdated++
            }
            lastProcessedDay = dayKey
            miningRepository.setLastMinedDayKey(dayKey)
        }

        // Final pass: collapse anything still fragmented after the day loop.
        consolidateExistingSlots(labelMap)

        return MiningResult(
            daysProcessed = pendingDays.size,
            slotsUpdated = slotsUpdated,
            lastMinedDayKey = lastProcessedDay,
        )
    }

    private suspend fun consolidateExistingSlots(labelMap: Map<String, String>) {
        val all = habitSlotRepository.listAll()
        if (all.isEmpty()) return

        val remapped = all.map { slot ->
            val canonical = resolveCanonical(slot, labelMap)
            slot.copy(canonicalLabel = canonical)
        }
        val byCanonical = remapped.groupBy { it.canonicalLabel }
        for ((canonical, group) in byCanonical) {
            if (canonical == "ignore_robot" ||
                RobotSelfActivityFilter.isRobotSelfLabel(canonical.replace('_', ' '))
            ) {
                group.forEach { habitSlotRepository.deleteBySlotKey(it.slotKey) }
                continue
            }
            val clusters = HabitSlotMergePolicy.clusterByTime(group)
            for (cluster in clusters) {
                val mergedBase = if (cluster.size == 1) {
                    cluster.first().copy(canonicalLabel = canonical)
                } else {
                    HabitSlotMergePolicy.mergeCluster(cluster).copy(canonicalLabel = canonical)
                }
                val merged = rewriteSlotKeyIfNeeded(mergedBase)
                cluster.forEach { member ->
                    if (member.slotKey != merged.slotKey) {
                        habitSlotRepository.deleteBySlotKey(member.slotKey)
                    }
                }
                habitSlotRepository.upsert(merged)
            }
        }
    }

    private fun resolveCanonical(slot: HabitSlot, labelMap: Map<String, String>): String {
        val candidates = buildList {
            add(slot.displayLabel)
            add(slot.canonicalLabel)
            add(slot.canonicalLabel.replace('_', ' '))
            addAll(slot.rawLabels)
        }
        val mapped = candidates.firstNotNullOfOrNull { label ->
            labelMap[label]?.takeIf { it.isNotBlank() }
        } ?: slot.canonicalLabel
        return HabitCanonicalPolicy.refineMealByTime(
            HabitCanonicalPolicy.applyLocalAliases(mapped),
            slot.typicalTimeMinutes,
        )
    }

    private fun rewriteSlotKeyIfNeeded(slot: HabitSlot): HabitSlot {
        val expected = HabitSlotKey.buildSlotKey(
            slot.canonicalLabel,
            HabitSlotKey.timeBucketMinutesFromClock(slot.typicalTimeMinutes),
        )
        return if (slot.slotKey == expected) slot else slot.copy(slotKey = expected)
    }
}
