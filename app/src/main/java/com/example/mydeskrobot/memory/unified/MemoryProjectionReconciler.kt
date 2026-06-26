package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.data.spatial.SpatialContextRepository
import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.domain.list.ListItemType

/**
 * Re-syncs operational stores into cognitive projections (idempotent).
 */
class MemoryProjectionReconciler(
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val listItemRepository: ListItemRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val spatialPlaceRepository: SpatialPlaceRepository?,
    private val spatialContextRepository: SpatialContextRepository?,
) {

    suspend fun reconcileAll(): ReconcileResult {
        unifiedMemoryRepository.ensureMigrated()
        var repaired = 0
        var skipped = 0

        scheduledTaskRepository.listPending().forEach { task ->
            val ref = UnifiedMemoryRepository.reminderExternalRef(task.id)
            if (!unifiedMemoryRepository.verifyProjection(ref, expectedActive = true)) {
                unifiedMemoryRepository.saveReminderProjection(
                    taskId = task.id,
                    message = task.message,
                    triggerAtMillis = task.triggerAtMillis,
                    source = MemoryDocumentSource.SYSTEM,
                )
                repaired++
            } else {
                skipped++
            }
        }

        listItemRepository.list(limit = ListItemRepository.MAX_LIMIT).forEach { item ->
            val ref = UnifiedMemoryRepository.listItemExternalRef(item.id)
            val shouldBeActive = item.type == ListItemType.NOTE || !item.checked
            if (!unifiedMemoryRepository.verifyProjection(ref, expectedActive = shouldBeActive)) {
                if (shouldBeActive) {
                    unifiedMemoryRepository.saveListItemProjection(
                        itemId = item.id,
                        type = item.type,
                        text = item.text,
                        checked = item.checked,
                        source = MemoryDocumentSource.SYSTEM,
                    )
                } else {
                    unifiedMemoryRepository.deactivateListItemProjection(item.id)
                }
                repaired++
            } else {
                skipped++
            }
        }

        val sinceMs = System.currentTimeMillis() - UnifiedMemoryRepository.EPISODE_RETENTION_MS
        activityLogRepository.getEventsGroupedByDay()
            .flatMap { it.events }
            .filter { it.timestampMs >= sinceMs }
            .forEach { event ->
                if (syncEpisodeProjection(event)) {
                    repaired++
                } else {
                    skipped++
                }
            }

        spatialPlaceRepository?.listActive()?.forEach { place ->
            val ref = UnifiedMemoryRepository.spatialPlaceExternalRef(place.id)
            if (!unifiedMemoryRepository.verifyProjection(ref, expectedActive = true)) {
                unifiedMemoryRepository.saveSpatialPlaceProjection(
                    placeId = place.id,
                    label = place.label,
                    landmarks = place.landmarks,
                    roomType = place.roomType.name.lowercase(),
                    description = place.description,
                    source = MemoryDocumentSource.SYSTEM,
                )
                repaired++
            } else {
                skipped++
            }
        }

        val snapshot = spatialContextRepository?.load()
        val currentLabel = snapshot?.currentPlaceLabel
        if (currentLabel.isNullOrBlank()) {
            skipped++
        } else {
            val ref = UnifiedMemoryRepository.SPATIAL_CURRENT_EXTERNAL_REF
            if (!unifiedMemoryRepository.verifyProjection(ref, expectedActive = true)) {
                unifiedMemoryRepository.saveCurrentPlaceProjection(
                    placeId = snapshot.currentPlaceId,
                    label = currentLabel,
                    roomType = snapshot.roomType?.name?.lowercase(),
                    confidence = snapshot.confidence,
                    source = MemoryDocumentSource.SYSTEM,
                )
                repaired++
            } else {
                skipped++
            }
        }

        activityLogRepository.getHabitSummary()?.let { profile ->
            val ref = UnifiedMemoryRepository.HABIT_SUMMARY_EXTERNAL_REF
            if (!unifiedMemoryRepository.verifyProjection(ref, expectedActive = true)) {
                unifiedMemoryRepository.saveHabitSummaryProjection(
                    summaryText = profile.summaryText,
                    sourceEventCount = profile.sourceEventCount,
                    source = MemoryDocumentSource.SYSTEM,
                )
                repaired++
            } else {
                skipped++
            }
        }

        return ReconcileResult(repaired = repaired, skipped = skipped)
    }

    suspend fun reconcileEpisodes(): ReconcileResult {
        unifiedMemoryRepository.ensureMigrated()
        var repaired = 0
        var skipped = 0
        val sinceMs = System.currentTimeMillis() - UnifiedMemoryRepository.EPISODE_RETENTION_MS
        activityLogRepository.getEventsGroupedByDay()
            .flatMap { it.events }
            .filter { it.timestampMs >= sinceMs }
            .forEach { event ->
                if (syncEpisodeProjection(event)) {
                    repaired++
                } else {
                    skipped++
                }
            }
        return ReconcileResult(repaired = repaired, skipped = skipped)
    }

    private suspend fun syncEpisodeProjection(
        event: com.example.mydeskrobot.domain.activitylog.ActivityLogEntry,
    ): Boolean {
        if (unifiedMemoryRepository.isEpisodeProjectionCurrent(
                eventId = event.id,
                label = event.label,
                rawPhrase = event.rawPhrase,
            )
        ) {
            return false
        }
        unifiedMemoryRepository.saveEpisodeProjection(
            eventId = event.id,
            label = event.label,
            eventKind = event.eventKind,
            dayKey = event.dayKey,
            timestampMs = event.timestampMs,
            confidence = event.confidence,
            scheduledDayKey = event.scheduledDayKey,
            scheduledAtMs = event.scheduledAtMs,
            actor = event.actor,
            sourceChannel = event.sourceChannel,
            rawPhrase = event.rawPhrase,
            source = MemoryDocumentSource.SYSTEM,
        )
        return true
    }

    data class ReconcileResult(
        val repaired: Int,
        val skipped: Int,
    )
}
