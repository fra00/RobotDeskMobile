package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.domain.awareness.UserAwarenessState
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.domain.mood.RobotMood
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.model.RobotInput
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Assembles the context payload for a heartbeat tick.
 */
class HeartbeatContextBuilder(
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
    private val lastInteractionProvider: () -> Long,
    private val currentMoodProvider: (suspend () -> RobotMood?)? = null,
    private val workingMemoryProvider: (suspend () -> WorkingMemory?)? = null,
    private val userAwarenessProvider: (suspend () -> UserAwarenessState?)? = null,
    private val activityLogRepository: ActivityLogRepository? = null,
    private val spatialSnapshotProvider: (suspend () -> com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot)? = null,
    private val knownPlacesProvider: (suspend () -> List<String>)? = null,
) {
    suspend fun build(): RobotInput.Heartbeat {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        val lastInteraction = lastInteractionProvider()
        val minutesSinceLastInteraction = if (lastInteraction > 0L) {
            (now - lastInteraction) / 60_000L
        } else {
            0L
        }

        val pendingReminders = scheduledTaskRepository.listPending()
        val routineStrings = unifiedMemoryRepository
            .getToolByCategory(MemoryCategory.ROUTINE, MAX_ROUTINES)
            .map { it.value }
        val activeIntents = unifiedMemoryRepository
            .getToolByCategory(MemoryCategory.INTENT, MAX_INTENTS)
            .map { it.value }
        val recentObservations = unifiedMemoryRepository
            .getRecentObservations(MAX_OBSERVATIONS)
            .map { it.value }
        val activePatterns = unifiedMemoryRepository
            .getToolByCategory(MemoryCategory.PATTERN, MAX_PATTERNS)
            .map { it.value }

        val dayOfWeek = formatDayOfWeek(calendar)

        val mood = currentMoodProvider?.invoke()
        val moodLabel = mood?.baseEmotion?.name?.lowercase()
        val moodIntensity = mood?.intensity
        val moodValence = mood?.valence

        val workingMemory = workingMemoryProvider?.invoke()

        val userAwareness = userAwarenessProvider?.invoke()
        val userMood = userAwareness?.inferredMood?.name?.lowercase()
        val userKnows = userAwareness?.userProbablyKnows?.toList()?.take(MAX_USER_KNOWS)

        val habitSummary = activityLogRepository?.getHabitSummary()?.summaryText
        val recentActivities = activityLogRepository
            ?.getRecentPhysicalForContext(maxEvents = MAX_RECENT_ACTIVITIES, daysBack = 1)
            ?.map { event ->
                val time = activityTimeFormat.format(event.timestampMs)
                "$time ${event.label}"
            }
            .orEmpty()

        val spatialSnapshot = spatialSnapshotProvider?.invoke()
        val knownPlaces = knownPlacesProvider?.invoke().orEmpty()

        return RobotInput.Heartbeat(
            minutesSinceLastInteraction = minutesSinceLastInteraction,
            currentHour = calendar.get(Calendar.HOUR_OF_DAY),
            currentMinute = calendar.get(Calendar.MINUTE),
            dayOfWeek = dayOfWeek,
            pendingRemindersCount = pendingReminders.size,
            relevantRoutines = routineStrings,
            moodLabel = moodLabel,
            moodIntensity = moodIntensity,
            moodValence = moodValence,
            todayInteractions = workingMemory?.todayInteractions ?: 0,
            proactiveSpeaksToday = workingMemory?.proactiveSpeaksToday ?: 0,
            topicsDiscussedToday = workingMemory?.topicsDiscussedToday ?: emptyList(),
            minutesSinceLastProactiveSpeak = workingMemory?.minutesSinceLastProactiveSpeak(now),
            userMood = userMood,
            userProbablyKnows = userKnows ?: emptyList(),
            activeIntents = activeIntents,
            recentObservations = recentObservations,
            activePatterns = activePatterns,
            habitProfileSummary = habitSummary,
            recentDailyActivities = recentActivities,
            currentPlaceLabel = spatialSnapshot?.currentPlaceLabel,
            placeConfidence = spatialSnapshot?.confidence?.takeIf { it > 0f },
            knownPlaces = knownPlaces,
            timestamp = now,
        )
    }

    private fun formatDayOfWeek(calendar: Calendar): String {
        val sdf = SimpleDateFormat("EEEE", Locale.ITALIAN)
        return sdf.format(calendar.time)
    }

    companion object {
        private const val MAX_ROUTINES = 5
        private const val MAX_USER_KNOWS = 10
        private const val MAX_INTENTS = 3
        private const val MAX_OBSERVATIONS = 8
        private const val MAX_PATTERNS = 3
        private const val MAX_RECENT_ACTIVITIES = 4
        private val activityTimeFormat = SimpleDateFormat("HH:mm", Locale.ITALY)
    }
}
