package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.data.scheduled.db.ScheduledTaskEntity
import com.example.mydeskrobot.domain.pending.PendingInboxItem
import com.example.mydeskrobot.domain.pending.PendingInboxKind
import com.example.mydeskrobot.integration.input.DeferredQueueItem
import com.example.mydeskrobot.reasoning.model.RobotInput
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PendingInboxMapper {

    private val timeTodayFormat = SimpleDateFormat("HH:mm", Locale.ITALY)
    private val timeFullFormat = SimpleDateFormat("d MMM HH:mm", Locale.ITALY)

    fun fromReminders(entities: List<ScheduledTaskEntity>): List<PendingInboxItem> =
        entities.map { task ->
            PendingInboxItem(
                id = reminderId(task.id),
                kind = PendingInboxKind.REMINDER,
                timeMillis = task.triggerAtMillis,
                title = "Promemoria",
                body = task.message,
            )
        }

    fun fromDeferredItems(items: List<DeferredQueueItem>): List<PendingInboxItem> =
        items.mapNotNull { item ->
            when (val input = item.envelope.input) {
                is RobotInput.Notification -> PendingInboxItem(
                    id = deferredId(item.envelope.dedupKey),
                    kind = PendingInboxKind.NOTIFICATION,
                    timeMillis = item.enqueuedAt,
                    title = input.appLabel,
                    body = buildNotificationBody(input.title, input.text),
                )
                is RobotInput.ScheduledTaskFired -> null
                else -> PendingInboxItem(
                    id = deferredId(item.envelope.dedupKey),
                    kind = PendingInboxKind.NOTIFICATION,
                    timeMillis = item.enqueuedAt,
                    title = input.sourceId,
                    body = item.envelope.formattedForLlm.lineSequence().drop(1).firstOrNull().orEmpty(),
                )
            }
        }

    fun toUi(item: PendingInboxItem, kindLabel: String): PendingInboxItemUi =
        PendingInboxItemUi(
            id = item.id,
            kindLabel = kindLabel,
            timeLabel = formatTime(item.timeMillis),
            body = item.body,
            title = item.title,
        )

    fun parseReminderId(id: String): Long? =
        id.removePrefix(REMINDER_PREFIX).toLongOrNull()

    fun parseDeferredDedupKey(id: String): String? =
        id.takeIf { it.startsWith(DEFERRED_PREFIX) }?.removePrefix(DEFERRED_PREFIX)

    private fun reminderId(taskId: Long) = "$REMINDER_PREFIX$taskId"

    private fun deferredId(dedupKey: String) = "$DEFERRED_PREFIX$dedupKey"

    private fun buildNotificationBody(title: String?, text: String?): String {
        val parts = listOfNotNull(title?.trim()?.takeIf { it.isNotBlank() }, text?.trim()?.takeIf { it.isNotBlank() })
        return parts.joinToString(" — ")
    }

    private fun formatTime(timestampMs: Long): String {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.timeInMillis = timestampMs
        val sameDay = calendar.get(Calendar.DAY_OF_YEAR) == today &&
            calendar.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
        return if (sameDay) {
            timeTodayFormat.format(timestampMs)
        } else {
            timeFullFormat.format(timestampMs)
        }
    }

    private const val REMINDER_PREFIX = "reminder:"
    private const val DEFERRED_PREFIX = "deferred:"
}

data class PendingInboxItemUi(
    val id: String,
    val kindLabel: String,
    val timeLabel: String,
    val title: String,
    val body: String,
)
