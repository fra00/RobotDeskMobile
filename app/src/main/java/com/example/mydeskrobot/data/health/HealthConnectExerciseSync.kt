package com.example.mydeskrobot.data.health

import android.util.Log
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import com.example.mydeskrobot.memory.unified.UnifiedMemoryWriter
import java.util.concurrent.TimeUnit

/**
 * Writes one Health Connect exercise session into Log Day (+ optional cognitive projection).
 */
fun interface HealthConnectEpisodeSink {
    /**
     * @return true if a new episode was written (false if skipped / failed).
     */
    suspend fun writeExercise(
        label: String,
        rawPhrase: String,
        sourceChannel: String,
        startTimeMs: Long,
    ): Boolean
}

/**
 * Syncs Health Connect exercise sessions into Log Day as confirmed PHYSICAL_NOW episodes.
 */
class HealthConnectExerciseSync(
    private val reader: HealthConnectExerciseReader,
    private val activityLogRepository: ActivityLogRepository,
    private val episodeSink: HealthConnectEpisodeSink,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {

    constructor(
        reader: HealthConnectExerciseReader,
        activityLogRepository: ActivityLogRepository,
        memoryWriter: UnifiedMemoryWriter,
        clockMs: () -> Long = { System.currentTimeMillis() },
    ) : this(
        reader = reader,
        activityLogRepository = activityLogRepository,
        episodeSink = UnifiedMemoryHealthConnectSink(memoryWriter),
        clockMs = clockMs,
    )

    data class Result(
        val skippedUnavailable: Boolean = false,
        val skippedNoPermission: Boolean = false,
        val inserted: Int = 0,
        val skippedDuplicate: Int = 0,
        val readCount: Int = 0,
    )

    /**
     * Best-effort sync of the last [RETENTION_DAYS] days. Never throws.
     */
    suspend fun syncLastRetentionWindow(): Result {
        if (!reader.isAvailable()) {
            return Result(skippedUnavailable = true)
        }
        if (!reader.hasExerciseReadPermission()) {
            return Result(skippedNoPermission = true)
        }
        return runCatching {
            val endMs = clockMs()
            val startMs = endMs - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
            val sessions = reader.readSessions(startMs, endMs)
            var inserted = 0
            var skippedDuplicate = 0
            for (session in sessions) {
                val sourceChannel = sourceChannelFor(session.metadataId)
                if (activityLogRepository.findBySourceChannel(sourceChannel) != null) {
                    skippedDuplicate++
                    continue
                }
                val label = HealthConnectExerciseLabelMapper.labelForExerciseType(session.exerciseType)
                val rawPhrase = HealthConnectExerciseLabelMapper.formatRawPhrase(
                    label = label,
                    startTimeMs = session.startTimeMs,
                    endTimeMs = session.endTimeMs,
                    title = session.title,
                )
                val wrote = episodeSink.writeExercise(
                    label = label,
                    rawPhrase = rawPhrase,
                    sourceChannel = sourceChannel,
                    startTimeMs = session.startTimeMs,
                )
                if (wrote) inserted++
            }
            Result(
                inserted = inserted,
                skippedDuplicate = skippedDuplicate,
                readCount = sessions.size,
            )
        }.getOrElse { e ->
            Log.w(TAG, "Health Connect exercise sync failed", e)
            Result()
        }
    }

    companion object {
        private const val TAG = "HealthConnectSync"
        const val RETENTION_DAYS = 7L
        const val SOURCE_CHANNEL_PREFIX = "health_connect:"

        fun sourceChannelFor(metadataId: String): String =
            SOURCE_CHANNEL_PREFIX + metadataId.trim()
    }
}

private class UnifiedMemoryHealthConnectSink(
    private val memoryWriter: UnifiedMemoryWriter,
) : HealthConnectEpisodeSink {
    override suspend fun writeExercise(
        label: String,
        rawPhrase: String,
        sourceChannel: String,
        startTimeMs: Long,
    ): Boolean {
        val write = memoryWriter.saveEpisode(
            label = label,
            rawPhrase = rawPhrase,
            source = ActivitySource.HEALTH_CONNECT,
            eventKind = EpisodeKind.PHYSICAL_NOW,
            confidence = EpisodeConfidence.CONFIRMED,
            sourceChannel = sourceChannel,
            memorySource = MemoryDocumentSource.SYSTEM,
            timestampMs = startTimeMs,
            externalRefOverride = sourceChannel,
        )
        return write.eventId > 0L
    }
}
