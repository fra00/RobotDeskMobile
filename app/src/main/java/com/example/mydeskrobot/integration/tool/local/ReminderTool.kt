package com.example.mydeskrobot.integration.tool.local

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reminder tool that schedules a notification via [AlarmManager].
 * Does NOT depend on the system Clock app, so it works on any device.
 *
 * Supports two scheduling modes:
 * - Relative: "delay_minutes" (preferred for "tra X minuti")
 * - Absolute: "hour" + "minute" (preferred for "alle X:Y")
 */
class ReminderTool(
    private val context: Context,
) : Tool {

    override val name: String = "set_reminder"
    override val locality: ToolLocality = ToolLocality.LOCAL

    private val requestCode = AtomicInteger(7000)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Schedule a reminder notification at a specific time or after a delay. " +
                "Use 'delay_minutes' for relative reminders (e.g. 'tra 10 minuti'), or 'hour' + 'minute' for absolute times.",
            parameters = listOf(
                ToolParameter(
                    name = "message",
                    type = "string",
                    description = "Reminder message shown in the notification",
                    required = true,
                ),
                ToolParameter(
                    name = "delay_minutes",
                    type = "integer",
                    description = "Minutes from now (use this for 'tra X minuti'). Mutually exclusive with hour/minute.",
                    required = false,
                ),
                ToolParameter(
                    name = "hour",
                    type = "integer",
                    description = "Hour 0-23 (use with 'minute' for absolute time like 'alle 7:30')",
                    required = false,
                ),
                ToolParameter(
                    name = "minute",
                    type = "integer",
                    description = "Minute 0-59 (use with 'hour' for absolute time)",
                    required = false,
                ),
            ),
            returns = "success (boolean), scheduled_time (string HH:mm)",
            example = """{"name": "set_reminder", "params": {"message": "Prendi le medicine", "delay_minutes": 10}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        Log.i(TAG, "execute called with params=${invocation.params}")
        
        val message = invocation.params["message"]?.toString()?.trim()
            ?: return ToolResult.Error(
                message = "Parametro 'message' mancante",
                code = "MISSING_PARAM",
            )

        val triggerMillis = computeTriggerMillis(invocation.params)
            ?: return ToolResult.Error(
                message = "Specifica 'delay_minutes' oppure 'hour' + 'minute'",
                code = "MISSING_TIME",
            )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return ToolResult.Error(
                message = "AlarmManager non disponibile",
                code = "SYSTEM_ERROR",
                recoverable = false,
            )

        return try {
            val id = requestCode.getAndIncrement()
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_REMINDER
                putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, message)
                putExtra(ReminderAlarmReceiver.EXTRA_NOTIFICATION_ID, id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            scheduleAlarm(alarmManager, triggerMillis, pendingIntent)
            Log.i(TAG, "Promemoria programmato per ${timeFormat.format(Date(triggerMillis))}: $message")

            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "message" to message,
                    "scheduled_time" to timeFormat.format(Date(triggerMillis)),
                    "reminder_id" to id,
                )
            )
        } catch (e: SecurityException) {
            ToolResult.Error(
                message = "Permesso per allarmi esatti non concesso. Vai nelle impostazioni di sistema.",
                code = "PERMISSION_DENIED",
                recoverable = false,
            )
        } catch (e: Exception) {
            ToolResult.Error(
                message = "Impossibile impostare il promemoria: ${e.message}",
                code = "REMINDER_ERROR",
                recoverable = true,
            )
        }
    }

    private fun computeTriggerMillis(params: Map<String, Any?>): Long? {
        val delayMinutes = (params["delay_minutes"] as? Number)?.toInt()
        if (delayMinutes != null && delayMinutes > 0) {
            return System.currentTimeMillis() + delayMinutes * 60_000L
        }

        val hour = (params["hour"] as? Number)?.toInt()
        val minute = (params["minute"] as? Number)?.toInt()
        if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return calendar.timeInMillis
        }

        return null
    }

    companion object {
        private const val TAG = "ReminderTool"
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent,
                )
                return
            }
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMillis,
            pendingIntent,
        )
    }
}
