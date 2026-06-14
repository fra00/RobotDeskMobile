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
    ): Long {
        val normalizedLabel = normalizeLabel(label)
        if (normalizedLabel.isBlank()) return -1L

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
            ),
        )
        pruneExpired()
        return id
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

    suspend fun getRecentForContext(maxEvents: Int, daysBack: Int = 2): List<ActivityLogEntry> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysBack)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return dao.getSince(calendar.timeInMillis)
            .map { it.toDomain() }
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

        fun create(context: Context): ActivityLogRepository {
            val db = Room.databaseBuilder(
                context.applicationContext,
                ActivityLogDatabase::class.java,
                "activity_log.db",
            ).build()
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
    )

private fun ActivityHabitProfileEntity.toDomain(): ActivityHabitProfile =
    ActivityHabitProfile(
        summaryText = summaryText,
        updatedAtMs = updatedAtMs,
        sourceEventCount = sourceEventCount,
    )
