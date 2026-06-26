package com.example.mydeskrobot.data.activitylog

import android.content.Context
import androidx.room.Room
import com.example.mydeskrobot.data.activitylog.db.ActivityHabitProfileEntity
import com.example.mydeskrobot.data.activitylog.db.ActivityLogDao
import com.example.mydeskrobot.data.activitylog.db.ActivityLogDatabase
import com.example.mydeskrobot.data.activitylog.db.ActivityLogEventEntity
import com.example.mydeskrobot.domain.activitylog.ActivityHabitProfile
import com.example.mydeskrobot.domain.activitylog.ActivityLogEntry
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.DayActivityGroup
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class ActivityLogRepository(
    private val dao: ActivityLogDao,
) {
    suspend fun appendEvent(
        label: String,
        rawPhrase: String? = null,
        source: ActivitySource,
        timestampMs: Long = System.currentTimeMillis(),
        eventKind: EpisodeKind = EpisodeKind.PHYSICAL_NOW,
        confidence: EpisodeConfidence = EpisodeConfidence.CONFIRMED,
        scheduledAtMs: Long? = null,
        scheduledDayKey: String? = null,
        actor: String? = null,
        sourceChannel: String? = null,
        isUnread: Boolean = false,
    ): Long {
        val normalizedLabel = normalizeLabel(label)
        if (normalizedLabel.isBlank()) return -1L

        if (eventKind != EpisodeKind.PHYSICAL_NOW) {
            return upsertEpisodicEvent(
                label = normalizedLabel,
                rawPhrase = rawPhrase,
                source = source,
                timestampMs = timestampMs,
                eventKind = eventKind,
                confidence = confidence,
                scheduledAtMs = scheduledAtMs,
                scheduledDayKey = scheduledDayKey,
                actor = normalizeActor(actor),
                sourceChannel = sourceChannel?.trim()?.takeIf { it.isNotBlank() },
                isUnread = isUnread,
            )
        }

        val dayKey = dayKeyFor(timestampMs)
        val existing = dao.findLatestByDayAndLabel(dayKey, normalizedLabel)
        if (existing != null && timestampMs - existing.timestampMs < DEDUP_WINDOW_MS) {
            return existing.id
        }

        val id = dao.insert(
            ActivityLogEventEntity(
                dayKey = dayKey,
                timestampMs = timestampMs,
                label = normalizedLabel,
                rawPhrase = rawPhrase?.trim()?.takeIf { it.isNotBlank() },
                source = source,
                eventKind = eventKind,
                confidence = confidence,
                scheduledAtMs = scheduledAtMs,
                scheduledDayKey = scheduledDayKey,
                actor = normalizeActor(actor),
                sourceChannel = sourceChannel?.trim()?.takeIf { it.isNotBlank() },
                isUnread = isUnread,
            ),
        )
        pruneExpired()
        return id
    }

    suspend fun upsertEpisodicEvent(
        label: String,
        rawPhrase: String? = null,
        source: ActivitySource,
        timestampMs: Long = System.currentTimeMillis(),
        eventKind: EpisodeKind,
        confidence: EpisodeConfidence = EpisodeConfidence.TENTATIVE,
        scheduledAtMs: Long? = null,
        scheduledDayKey: String? = null,
        actor: String? = null,
        sourceChannel: String? = null,
        isUnread: Boolean = false,
    ): Long {
        val normalizedLabel = normalizeLabel(label)
        if (normalizedLabel.isBlank()) return -1L
        val dayKey = dayKeyFor(timestampMs)
        val normalizedActor = normalizeActor(actor)
        val trimmedPhrase = rawPhrase?.trim()?.takeIf { it.isNotBlank() }
        val trimmedChannel = sourceChannel?.trim()?.takeIf { it.isNotBlank() }
        val targetDayKey = scheduledDayKey?.trim()?.takeIf { it.isNotBlank() } ?: dayKey

        val existing = dao.findEpisodicForMerge(
            scheduledDayKey = targetDayKey,
            eventKind = eventKind,
            label = normalizedLabel,
            actor = normalizedActor,
        )

        if (existing != null) {
            val mergedConfidence = mergeConfidence(existing.confidence, confidence)
            val mergedScheduledAt = scheduledAtMs ?: existing.scheduledAtMs
            val mergedPhrase = trimmedPhrase ?: existing.rawPhrase
            val mergedChannel = trimmedChannel ?: existing.sourceChannel
            dao.update(
                existing.copy(
                    timestampMs = timestampMs,
                    rawPhrase = mergedPhrase,
                    source = source,
                    confidence = mergedConfidence,
                    scheduledAtMs = mergedScheduledAt,
                    actor = normalizedActor ?: existing.actor,
                    sourceChannel = mergedChannel,
                    isUnread = isUnread || existing.isUnread,
                ),
            )
            pruneExpired()
            return existing.id
        }

        val id = dao.insert(
            ActivityLogEventEntity(
                dayKey = dayKey,
                timestampMs = timestampMs,
                label = normalizedLabel,
                rawPhrase = trimmedPhrase,
                source = source,
                eventKind = eventKind,
                confidence = confidence,
                scheduledAtMs = scheduledAtMs,
                scheduledDayKey = targetDayKey,
                actor = normalizedActor,
                sourceChannel = trimmedChannel,
                isUnread = isUnread,
            ),
        )
        pruneExpired()
        return id
    }

    suspend fun getUpcomingForDay(targetDayKey: String, limit: Int = 8): List<ActivityLogEntry> =
        dao.getUpcomingForDay(targetDayKey, limit).map { it.toDomain() }

    suspend fun getOpenSocialThreads(daysBack: Int = 2, limit: Int = 4): List<ActivityLogEntry> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysBack)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return dao.getOpenSocialThreads(calendar.timeInMillis, limit).map { it.toDomain() }
    }

    fun observeEventsLast7Days(): Flow<List<ActivityLogEntry>> {
        val sinceMs = System.currentTimeMillis() - RETENTION_MS
        return dao.observeSince(sinceMs).map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getEventsGroupedByDay(): List<DayActivityGroup> {
        val sinceMs = System.currentTimeMillis() - RETENTION_MS
        return dao.getSince(sinceMs)
            .map { it.toDomain() }
            .groupBy { it.dayKey }
            .map { (dayKey, events) ->
                DayActivityGroup(dayKey = dayKey, events = events.sortedByDescending { it.timestampMs })
            }
            .sortedByDescending { it.dayKey }
    }

    suspend fun getRecentPhysicalForContext(maxEvents: Int, daysBack: Int = 2): List<ActivityLogEntry> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysBack)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return dao.getSince(calendar.timeInMillis)
            .map { it.toDomain() }
            .filter { it.eventKind == EpisodeKind.PHYSICAL_NOW }
            .sortedByDescending { it.timestampMs }
            .take(maxEvents)
    }

    suspend fun getHabitSummary(): ActivityHabitProfile? =
        dao.getProfile()?.toDomain()

    suspend fun saveHabitSummary(summaryText: String, sourceEventCount: Int) {
        val trimmed = summaryText.trim()
        if (trimmed.isBlank()) return
        dao.upsertProfile(
            ActivityHabitProfileEntity(
                summaryText = trimmed,
                updatedAtMs = System.currentTimeMillis(),
                sourceEventCount = sourceEventCount,
            ),
        )
    }

    suspend fun countEventsInRetentionWindow(): Int {
        val sinceMs = System.currentTimeMillis() - RETENTION_MS
        return dao.countSince(sinceMs)
    }

    suspend fun getEventsForSummary(): List<DayActivityGroup> = getEventsGroupedByDay()

    suspend fun pruneExpired(): Int {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return dao.deleteOlderThan(cutoff)
    }

    suspend fun clearAll() {
        dao.deleteAllEvents()
        dao.deleteProfile()
    }

    suspend fun markEventRead(eventId: Long) {
        val event = dao.getById(eventId) ?: return
        if (!event.isUnread) return
        dao.update(event.copy(isUnread = false))
    }

    companion object {
        const val RETENTION_DAYS = 7
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
        private const val DEDUP_WINDOW_MS = 30 * 60_000L

        fun dayKeyFor(timestampMs: Long): String {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
            return format.format(timestampMs)
        }

        fun normalizeLabel(label: String): String =
            label.trim()
                .replace(Regex("\\s+"), " ")

        fun normalizeActor(actor: String?): String? =
            actor?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() }

        fun parseScheduledAtMs(scheduledDayKey: String?, scheduledTime: String?): Long? {
            val dayKey = scheduledDayKey?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val time = scheduledTime?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
            val dayDate = dayFormat.parse(dayKey) ?: return null
            val parts = time.split(":")
            if (parts.size < 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            val calendar = Calendar.getInstance(Locale.ITALY)
            calendar.time = dayDate
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }

        fun dayBoundsForDayKey(dayKey: String): Pair<Long, Long> {
            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
            val parsed = dayFormat.parse(dayKey)
                ?: return todayBoundsMillis()
            val calendar = Calendar.getInstance(Locale.ITALY)
            calendar.time = parsed
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val end = calendar.timeInMillis
            return start to end
        }

        fun todayBoundsMillis(): Pair<Long, Long> = dayBoundsForDayKey(dayKeyFor(System.currentTimeMillis()))

        private fun mergeConfidence(
            existing: EpisodeConfidence,
            incoming: EpisodeConfidence,
        ): EpisodeConfidence {
            if (existing == EpisodeConfidence.CONFIRMED || incoming == EpisodeConfidence.CONFIRMED) {
                return EpisodeConfidence.CONFIRMED
            }
            return EpisodeConfidence.TENTATIVE
        }

        fun create(context: Context): ActivityLogRepository {
            val db = Room.databaseBuilder(
                context.applicationContext,
                ActivityLogDatabase::class.java,
                "activity_log.db",
            )
                .addMigrations(
                    ActivityLogDatabase.MIGRATION_1_2,
                    ActivityLogDatabase.MIGRATION_2_3,
                )
                .build()
            return ActivityLogRepository(db.activityLogDao())
        }

        fun createForTest(dao: ActivityLogDao): ActivityLogRepository = ActivityLogRepository(dao)
    }
}

private fun ActivityLogEventEntity.toDomain(): ActivityLogEntry =
    ActivityLogEntry(
        id = id,
        dayKey = dayKey,
        timestampMs = timestampMs,
        label = label,
        rawPhrase = rawPhrase,
        source = source,
        eventKind = eventKind,
        confidence = confidence,
        scheduledAtMs = scheduledAtMs,
        scheduledDayKey = scheduledDayKey,
        actor = actor,
        sourceChannel = sourceChannel,
        isUnread = isUnread,
    )

private fun ActivityHabitProfileEntity.toDomain(): ActivityHabitProfile =
    ActivityHabitProfile(
        summaryText = summaryText,
        updatedAtMs = updatedAtMs,
        sourceEventCount = sourceEventCount,
    )
