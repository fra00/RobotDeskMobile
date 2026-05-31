package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.domain.awareness.UserAwarenessState
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.domain.mood.RobotMood
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.reasoning.model.RobotInput
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Assembles the context payload for a heartbeat tick.
 * Collects data from:
 * - Last interaction timestamp
 * - Pending reminders (ScheduledTaskRepository)
 * - User routines (UserMemoryRepository, ROUTINE category)
 * - Current robot mood (MoodRepository)
 * - Working memory (today's interactions, topics, proactive speaks)
 * - User awareness state (Theory of Mind)
 */
class HeartbeatContextBuilder(
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val memoryRepository: UserMemoryRepository,
    private val lastInteractionProvider: () -> Long,
    private val currentMoodProvider: (suspend () -> RobotMood?)? = null,
    private val workingMemoryProvider: (suspend () -> WorkingMemory?)? = null,
    private val userAwarenessProvider: (suspend () -> UserAwarenessState?)? = null,
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
        val routines = memoryRepository.getByCategory(MemoryCategory.ROUTINE, MAX_ROUTINES)
        val routineStrings = routines.map { it.value }

        val dayOfWeek = formatDayOfWeek(calendar)

        val mood = currentMoodProvider?.invoke()
        val moodLabel = mood?.baseEmotion?.name?.lowercase()
        val moodIntensity = mood?.intensity

        val workingMemory = workingMemoryProvider?.invoke()

        val userAwareness = userAwarenessProvider?.invoke()
        val userMood = userAwareness?.inferredMood?.name?.lowercase()
        val userKnows = userAwareness?.userProbablyKnows?.toList()?.take(MAX_USER_KNOWS)

        return RobotInput.Heartbeat(
            minutesSinceLastInteraction = minutesSinceLastInteraction,
            currentHour = calendar.get(Calendar.HOUR_OF_DAY),
            currentMinute = calendar.get(Calendar.MINUTE),
            dayOfWeek = dayOfWeek,
            pendingRemindersCount = pendingReminders.size,
            relevantRoutines = routineStrings,
            moodLabel = moodLabel,
            moodIntensity = moodIntensity,
            todayInteractions = workingMemory?.todayInteractions ?: 0,
            proactiveSpeaksToday = workingMemory?.proactiveSpeaksToday ?: 0,
            topicsDiscussedToday = workingMemory?.topicsDiscussedToday ?: emptyList(),
            minutesSinceLastProactiveSpeak = workingMemory?.minutesSinceLastProactiveSpeak(now),
            userMood = userMood,
            userProbablyKnows = userKnows ?: emptyList(),
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
    }
}
