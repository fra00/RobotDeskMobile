package com.example.mydeskrobot.memory.unified

import android.util.Log
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.domain.list.ListItemType
import com.example.mydeskrobot.memory.MemorySettingsRepository

/**
 * Single write path for operational store + cognitive index (`memory_documents`).
 */
class UnifiedMemoryWriter(
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val settingsRepository: MemorySettingsRepository?,
    private val projectionGuard: MemoryProjectionGuard? = null,
) {

    constructor(
        unifiedMemoryRepository: UnifiedMemoryRepository,
        activityLogRepository: ActivityLogRepository,
        settingsRepository: MemorySettingsRepository,
    ) : this(
        unifiedMemoryRepository = unifiedMemoryRepository,
        activityLogRepository = activityLogRepository,
        settingsRepository = settingsRepository,
        projectionGuard = MemoryProjectionGuard(unifiedMemoryRepository, settingsRepository),
    )

    suspend fun saveEpisode(
        label: String,
        rawPhrase: String? = null,
        source: ActivitySource,
        eventKind: EpisodeKind = EpisodeKind.PHYSICAL_NOW,
        confidence: EpisodeConfidence = EpisodeConfidence.CONFIRMED,
        scheduledAtMs: Long? = null,
        scheduledDayKey: String? = null,
        actor: String? = null,
        sourceChannel: String? = null,
        memorySource: MemoryDocumentSource = MemoryDocumentSource.TOOL,
        timestampMs: Long = System.currentTimeMillis(),
        isUnread: Boolean = false,
        externalRefOverride: String? = null,
    ): EpisodeWriteResult {
        val eventId = if (eventKind == EpisodeKind.PHYSICAL_NOW) {
            activityLogRepository.appendEvent(
                label = label,
                rawPhrase = rawPhrase,
                source = source,
                timestampMs = timestampMs,
                eventKind = eventKind,
                confidence = confidence,
                scheduledAtMs = scheduledAtMs,
                scheduledDayKey = scheduledDayKey,
                actor = actor,
                sourceChannel = sourceChannel,
                isUnread = isUnread,
            )
        } else {
            activityLogRepository.upsertEpisodicEvent(
                label = label,
                rawPhrase = rawPhrase,
                source = source,
                timestampMs = timestampMs,
                eventKind = eventKind,
                confidence = confidence,
                scheduledAtMs = scheduledAtMs,
                scheduledDayKey = scheduledDayKey,
                actor = actor,
                sourceChannel = sourceChannel,
                isUnread = isUnread,
            )
        }
        if (eventId < 0L) {
            return EpisodeWriteResult(eventId = -1L, indexOk = false, externalRef = "")
        }

        val normalizedLabel = ActivityLogRepository.normalizeLabel(label)
        val dayKey = ActivityLogRepository.dayKeyFor(timestampMs)
        val externalRef = externalRefOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: UnifiedMemoryRepository.activityLogExternalRef(eventId)
        val indexOk = projectEpisode(
            externalRef = externalRef,
            eventId = eventId,
            label = normalizedLabel,
            eventKind = eventKind,
            dayKey = dayKey,
            timestampMs = timestampMs,
            confidence = confidence,
            scheduledDayKey = scheduledDayKey,
            scheduledAtMs = scheduledAtMs,
            actor = actor,
            sourceChannel = sourceChannel,
            rawPhrase = rawPhrase,
            source = memorySource,
            isUnread = isUnread,
        )
        return EpisodeWriteResult(eventId = eventId, indexOk = indexOk, externalRef = externalRef)
    }

    suspend fun saveNotificationEpisode(
        appLabel: String,
        title: String?,
        text: String?,
        dedupKey: String,
        receivedAtMillis: Long,
    ): EpisodeWriteResult? {
        val externalRef = UnifiedMemoryRepository.notificationExternalRef(dedupKey)
        if (unifiedMemoryRepository.getByExternalRef(externalRef) != null) {
            return null
        }
        val actor = title?.trim()?.takeIf { it.isNotBlank() }
        val body = text?.trim()?.takeIf { it.isNotBlank() }
        val label = actor ?: appLabel.trim()
        if (label.isBlank()) return null

        return saveEpisode(
            label = label,
            rawPhrase = body,
            source = ActivitySource.NOTIFICATION,
            eventKind = EpisodeKind.SOCIAL_THREAD,
            confidence = EpisodeConfidence.CONFIRMED,
            actor = actor,
            sourceChannel = appLabel.trim(),
            memorySource = MemoryDocumentSource.SYSTEM,
            timestampMs = receivedAtMillis,
            isUnread = true,
            externalRefOverride = externalRef,
        )
    }

    suspend fun markEpisodeRead(externalRef: String) {
        val doc = unifiedMemoryRepository.getByExternalRef(externalRef)
        val linkedId = doc?.linkedActivityLogId
        unifiedMemoryRepository.markEpisodeRead(externalRef)
        linkedId?.let { activityLogRepository.markEventRead(it) }
    }

    suspend fun markAllNotificationEpisodesRead(): Int {
        val refs = unifiedMemoryRepository.listUnreadNotificationExternalRefs()
        refs.forEach { markEpisodeRead(it) }
        return refs.size
    }

    suspend fun onReminderScheduled(taskId: Long, message: String, triggerAtMillis: Long) {
        val ref = UnifiedMemoryRepository.reminderExternalRef(taskId)
        guarded(ref, expectedActive = true) {
            unifiedMemoryRepository.saveReminderProjection(
                taskId = taskId,
                message = message,
                triggerAtMillis = triggerAtMillis,
                source = MemoryDocumentSource.TOOL,
            )
        }
    }

    suspend fun onReminderCancelled(taskId: Long) {
        val ref = UnifiedMemoryRepository.reminderExternalRef(taskId)
        guarded(ref, expectedActive = false) {
            unifiedMemoryRepository.deactivateReminderProjection(taskId)
        }
    }

    suspend fun onReminderFired(taskId: Long, message: String, triggerAtMillis: Long) {
        onReminderCancelled(taskId)
        val label = "Promemoria: ${message.trim()}"
        saveEpisode(
            label = label,
            rawPhrase = message.trim(),
            source = ActivitySource.TOOL,
            eventKind = EpisodeKind.PHYSICAL_NOW,
            confidence = EpisodeConfidence.CONFIRMED,
            sourceChannel = "promemoria",
            memorySource = MemoryDocumentSource.TOOL,
            timestampMs = System.currentTimeMillis(),
        )
    }

    suspend fun onListItemAdded(itemId: Long, type: ListItemType, text: String, checked: Boolean) {
        val active = type == ListItemType.NOTE || !checked
        val ref = UnifiedMemoryRepository.listItemExternalRef(itemId)
        guarded(ref, expectedActive = active) {
            unifiedMemoryRepository.saveListItemProjection(
                itemId = itemId,
                type = type,
                text = text,
                checked = checked,
                source = MemoryDocumentSource.TOOL,
            )
        }
    }

    suspend fun onListItemUpdated(itemId: Long, type: ListItemType, text: String, checked: Boolean) {
        if (type != ListItemType.NOTE && checked) {
            onListItemRemoved(itemId)
            return
        }
        onListItemAdded(itemId, type, text, checked)
    }

    suspend fun onListItemRemoved(itemId: Long) {
        val ref = UnifiedMemoryRepository.listItemExternalRef(itemId)
        guarded(ref, expectedActive = false) {
            unifiedMemoryRepository.deactivateListItemProjection(itemId)
        }
    }

    suspend fun onPlaceSaved(
        placeId: Long,
        label: String,
        landmarks: List<String>,
        roomType: String?,
        description: String?,
    ) {
        val ref = UnifiedMemoryRepository.spatialPlaceExternalRef(placeId)
        guarded(ref, expectedActive = true) {
            unifiedMemoryRepository.saveSpatialPlaceProjection(
                placeId = placeId,
                label = label,
                landmarks = landmarks,
                roomType = roomType,
                description = description,
                source = MemoryDocumentSource.TOOL,
            )
        }
    }

    suspend fun onCurrentPlaceSet(
        placeId: Long?,
        label: String?,
        roomType: String?,
        confidence: Float,
    ) {
        val ref = UnifiedMemoryRepository.SPATIAL_CURRENT_EXTERNAL_REF
        val active = placeId != null && !label.isNullOrBlank()
        guarded(ref, expectedActive = active) {
            unifiedMemoryRepository.saveCurrentPlaceProjection(
                placeId = placeId,
                label = label,
                roomType = roomType,
                confidence = confidence,
                source = MemoryDocumentSource.TOOL,
            )
        }
    }

    suspend fun onHabitSummarySaved(summaryText: String, sourceEventCount: Int) {
        val ref = UnifiedMemoryRepository.HABIT_SUMMARY_EXTERNAL_REF
        guarded(ref, expectedActive = true) {
            unifiedMemoryRepository.saveHabitSummaryProjection(
                summaryText = summaryText,
                sourceEventCount = sourceEventCount,
                source = MemoryDocumentSource.EXTRACTOR,
            )
        }
    }

    private suspend fun projectEpisode(
        externalRef: String,
        eventId: Long,
        label: String,
        eventKind: EpisodeKind,
        dayKey: String,
        timestampMs: Long,
        confidence: EpisodeConfidence,
        scheduledDayKey: String?,
        scheduledAtMs: Long?,
        actor: String?,
        sourceChannel: String?,
        rawPhrase: String?,
        source: MemoryDocumentSource,
        isUnread: Boolean,
    ): Boolean {
        var indexOk = true
        val result = guarded(externalRef, expectedActive = true) {
            unifiedMemoryRepository.saveEpisodeProjection(
                eventId = eventId,
                label = label,
                eventKind = eventKind,
                dayKey = dayKey,
                timestampMs = timestampMs,
                confidence = confidence,
                scheduledDayKey = scheduledDayKey,
                scheduledAtMs = scheduledAtMs,
                actor = actor,
                sourceChannel = sourceChannel,
                rawPhrase = rawPhrase,
                source = source,
                isUnread = isUnread,
                externalRefOverride = externalRef,
                linkedActivityLogId = eventId,
            )
        }
        if (result is MemoryProjectionGuard.ProjectionResult.Drift) {
            indexOk = false
            logIndexFailure(externalRef, "projection drift after write")
        }
        return indexOk
    }

    private suspend fun guarded(
        externalRef: String,
        expectedActive: Boolean,
        write: suspend () -> Unit,
    ): MemoryProjectionGuard.ProjectionResult {
        val guard = projectionGuard
        if (guard == null) {
            return try {
                write()
                MemoryProjectionGuard.ProjectionResult.Success
            } catch (error: Exception) {
                logIndexFailure(externalRef, "projection write failed", error)
                MemoryProjectionGuard.ProjectionResult.Drift(externalRef)
            }
        }
        val result = guard.projectAndVerify(externalRef, expectedActive, write)
        if (result is MemoryProjectionGuard.ProjectionResult.Drift) {
            settingsRepository?.recordProjectionDrift()
        }
        return result
    }

    private suspend fun logIndexFailure(externalRef: String, message: String, error: Throwable? = null) {
        if (error != null) {
            Log.e(TAG, "$message: externalRef=$externalRef", error)
        } else {
            Log.e(TAG, "$message: externalRef=$externalRef")
        }
        settingsRepository?.recordProjectionDrift()
    }

    companion object {
        private const val TAG = "UnifiedMemoryWriter"

        fun create(
            context: android.content.Context,
            unifiedMemoryRepository: UnifiedMemoryRepository,
            activityLogRepository: ActivityLogRepository,
            settingsRepository: MemorySettingsRepository,
        ): UnifiedMemoryWriter = UnifiedMemoryWriter(
            unifiedMemoryRepository = unifiedMemoryRepository,
            activityLogRepository = activityLogRepository,
            settingsRepository = settingsRepository,
        )
    }
}

data class EpisodeWriteResult(
    val eventId: Long,
    val indexOk: Boolean,
    val externalRef: String,
)
