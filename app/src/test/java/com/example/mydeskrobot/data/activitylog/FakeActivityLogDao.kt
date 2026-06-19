package com.example.mydeskrobot.data.activitylog

import com.example.mydeskrobot.data.activitylog.db.ActivityHabitProfileEntity
import com.example.mydeskrobot.data.activitylog.db.ActivityLogDao
import com.example.mydeskrobot.data.activitylog.db.ActivityLogEventEntity
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeActivityLogDao(
    initial: List<ActivityLogEventEntity> = emptyList(),
) : ActivityLogDao {

    private val events = initial.map { it.copy() }.toMutableList()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L
    private var profile: ActivityHabitProfileEntity? = null
    private val eventsFlow = MutableStateFlow(events.toList())

    private fun emit() {
        eventsFlow.value = events.toList()
    }

    override suspend fun insert(event: ActivityLogEventEntity): Long {
        val id = if (event.id == 0L) nextId++ else event.id
        events += event.copy(id = id)
        emit()
        return id
    }

    override suspend fun update(event: ActivityLogEventEntity) {
        val index = events.indexOfFirst { it.id == event.id }
        if (index >= 0) {
            events[index] = event
            emit()
        }
    }

    override fun observeSince(sinceMs: Long): Flow<List<ActivityLogEventEntity>> =
        eventsFlow.map { list -> list.filter { it.timestampMs >= sinceMs }.sortedByDescending { it.timestampMs } }

    override suspend fun getSince(sinceMs: Long): List<ActivityLogEventEntity> =
        events.filter { it.timestampMs >= sinceMs }.sortedByDescending { it.timestampMs }

    override suspend fun findLatestByDayAndLabel(dayKey: String, label: String): ActivityLogEventEntity? =
        events.filter { it.dayKey == dayKey && it.label == label }
            .maxByOrNull { it.timestampMs }

    override suspend fun findEpisodicForMerge(
        scheduledDayKey: String,
        eventKind: EpisodeKind,
        label: String,
        actor: String?,
    ): ActivityLogEventEntity? =
        events.filter {
            it.scheduledDayKey == scheduledDayKey &&
                it.eventKind == eventKind &&
                it.label == label &&
                ((actor == null && it.actor == null) || (actor != null && it.actor == actor))
        }.maxByOrNull { it.timestampMs }

    override suspend fun getUpcomingForDay(targetDayKey: String, limit: Int): List<ActivityLogEventEntity> =
        events.filter {
            it.scheduledDayKey == targetDayKey &&
                it.eventKind in listOf(EpisodeKind.PLAN, EpisodeKind.SOCIAL_THREAD, EpisodeKind.COMMITMENT)
        }
            .sortedWith(
                compareBy<ActivityLogEventEntity> { it.scheduledAtMs == null }
                    .thenBy { it.scheduledAtMs ?: Long.MAX_VALUE }
                    .thenByDescending { it.timestampMs },
            )
            .take(limit)

    override suspend fun getOpenSocialThreads(sinceMs: Long, limit: Int): List<ActivityLogEventEntity> =
        events.filter { it.eventKind == EpisodeKind.SOCIAL_THREAD && it.timestampMs >= sinceMs }
            .sortedByDescending { it.timestampMs }
            .take(limit)

    override suspend fun countSince(sinceMs: Long): Int =
        events.count { it.timestampMs >= sinceMs }

    override suspend fun deleteOlderThan(cutoffMs: Long): Int {
        val before = events.size
        events.removeAll { it.timestampMs < cutoffMs }
        emit()
        return before - events.size
    }

    override suspend fun deleteAllEvents() {
        events.clear()
        emit()
    }

    override suspend fun getProfile(id: Int): ActivityHabitProfileEntity? =
        profile?.takeIf { it.id == id }

    override suspend fun upsertProfile(newProfile: ActivityHabitProfileEntity) {
        profile = newProfile
    }

    override suspend fun deleteProfile() {
        profile = null
    }
}
