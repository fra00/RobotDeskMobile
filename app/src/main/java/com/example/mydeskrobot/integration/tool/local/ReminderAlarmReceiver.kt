package com.example.mydeskrobot.integration.tool.local

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.MainActivity
import com.example.mydeskrobot.R
import com.example.mydeskrobot.data.check.FireAndCheckRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskStatus
import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent
import com.example.mydeskrobot.integration.input.scheduled.ScheduledTaskInputSource
import com.example.mydeskrobot.memory.unified.UnifiedMemoryFactory
import com.example.mydeskrobot.reasoning.model.RobotInput
import kotlinx.coroutines.runBlocking

/**
 * Fires a scheduled task: system notification + optional voice via [SystemInputDispatcher].
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_REMINDER) return

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0L) {
            Log.w(TAG, "Missing task id in alarm intent")
            return
        }

        val repository = ScheduledTaskRepository.create(context)
        val task = runBlocking { repository.getById(taskId) }
        if (task == null || task.status != ScheduledTaskStatus.PENDING) {
            Log.d(TAG, "Task $taskId not pending, skipping")
            return
        }

        runBlocking { repository.markFired(taskId) }
        runBlocking { FireAndCheckRepository.create(context).onReminderFired(taskId) }

        val message = task.message.ifBlank { "Promemoria" }
        runBlocking {
            UnifiedMemoryFactory.createWriter(context).onReminderFired(
                taskId = taskId,
                message = message,
                triggerAtMillis = task.triggerAtMillis,
            )
        }

        showNotification(context, taskId, message)

        val fired = RobotInput.ScheduledTaskFired(
            taskId = taskId,
            message = message,
            triggerAtMillis = task.triggerAtMillis,
        )
        val inputSource = ScheduledTaskInputSource()
        if (!inputSource.shouldAccept(fired)) return

        val envelope = inputSource.toEnvelope(fired)
        Log.i(TAG, "Dispatching scheduled task $taskId to system input bus")
        SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
    }

    private fun showNotification(context: Context, notificationId: Long, message: String) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notifId = ScheduledTaskNotificationId.forTask(notificationId)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            notifId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Promemoria")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setAutoCancel(true)
            .setContentIntent(pendingContentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Promemoria robot",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Promemoria programmati dal robot da scrivania"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private object ScheduledTaskNotificationId {
        fun forTask(taskId: Long): Int = (BASE + (taskId and 0xFFFF)).toInt()

        private const val BASE = 6_000
    }

    companion object {
        private const val TAG = "ReminderAlarmRx"
        const val ACTION_FIRE_REMINDER = "com.example.mydeskrobot.action.FIRE_REMINDER"
        const val EXTRA_TASK_ID = "task_id"
        const val CHANNEL_ID = "mydeskrobot_reminders"
    }
}
