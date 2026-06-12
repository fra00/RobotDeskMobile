package com.example.mydeskrobot.integration.context

import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.domain.list.ListItemType
import com.example.mydeskrobot.reasoning.DayContextProvider
import com.example.mydeskrobot.reasoning.memory.MemoryIntentDetector
import com.example.mydeskrobot.reasoning.memory.MemoryRetrievalProfile
import java.util.Calendar

class DayContextPromptProviderImpl(
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val listItemRepository: ListItemRepository,
) : DayContextProvider {

    override suspend fun buildContextSection(userText: String): String {
        val detection = MemoryIntentDetector.detect(userText)
        if (!detection.includes(MemoryRetrievalProfile.PLAN)) {
            return ""
        }

        val (startOfDay, endOfDay) = todayBoundsMillis()
        val reminders = scheduledTaskRepository.listPendingForDay(startOfDay, endOfDay)
        val todos = listItemRepository.list(type = ListItemType.TODO, checked = false, limit = 8)
        val notes = listItemRepository.list(type = ListItemType.NOTE, limit = 5)

        if (reminders.isEmpty() && todos.isEmpty() && notes.isEmpty()) {
            return ""
        }

        return buildString {
            appendLine("TODAY CONTEXT:")
            reminders.forEach { task ->
                val time = scheduledTaskRepository.formatScheduledTime(task.triggerAtMillis)
                appendLine("- $time ${task.message}")
            }
            todos.forEach { item ->
                appendLine("- TODO: ${item.text}")
            }
            notes.forEach { item ->
                appendLine("- NOTE: ${item.text}")
            }
        }.trim()
    }

    private fun todayBoundsMillis(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val end = calendar.timeInMillis
        return start to end
    }
}
