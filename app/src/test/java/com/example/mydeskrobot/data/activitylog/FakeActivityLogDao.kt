package com.example.mydeskrobot.data.activitylog

import com.example.mydeskrobot.data.activitylog.db.ActivityHabitProfileEntity
import com.example.mydeskrobot.data.activitylog.db.ActivityLogDao
import com.example.mydeskrobot.data.activitylog.db.ActivityLogEventEntity
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

    override fun observeSince(sinceMs: Long): Flow<List<ActivityLogEventEntity>> =
        eventsFlow.map { list -> list.filter { it.timestampMs >= sinceMs }.sortedByDescending { it.timestampMs } }

    override suspend fun getSince(sinceMs: Long): List<ActivityLogEventEntity> =
        events.filter { it.timestampMs >= sinceMs }.sortedByDescending { it.timestampMs }

    override suspend fun findLatestByDayAndLabel(dayKey: String, label: String): ActivityLogEventEntity? =
        events.filter { it.dayKey == dayKey && it.label == label }
            .maxByOrNull { it.timestampMs }

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
