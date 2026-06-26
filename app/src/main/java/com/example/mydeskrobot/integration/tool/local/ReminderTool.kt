package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import android.util.Log
import com.example.mydeskrobot.data.check.FireAndCheckRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskAlarmScheduler
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.memory.unified.UnifiedMemoryWriter
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter
import java.util.Calendar

/**
 * Schedules a deferred announce task (voice + notification at fire time).
 */
class ReminderTool(
    private val context: Context,
    private val repository: ScheduledTaskRepository = ScheduledTaskRepository.create(context),
    private val fireAndCheckRepository: FireAndCheckRepository = FireAndCheckRepository.create(context),
    private val memoryWriter: UnifiedMemoryWriter,
) : Tool {

    override val name: String = "set_reminder"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Schedule a reminder the robot will announce aloud at the given time. " +
                "Use 'delay_minutes' for relative times, or 'hour' + 'minute' for absolute times.",
            parameters = listOf(
                ToolParameter(
                    name = "message",
                    type = "string",
                    description = "Reminder message",
                    required = true,
                ),
                ToolParameter(
                    name = "delay_minutes",
                    type = "integer",
                    description = "Minutes from now (e.g. 'tra 10 minuti')",
                    required = false,
                ),
                ToolParameter(
                    name = "hour",
                    type = "integer",
                    description = "Hour 0-23 with minute for absolute time",
                    required = false,
                ),
                ToolParameter(
                    name = "minute",
                    type = "integer",
                    description = "Minute 0-59 with hour",
                    required = false,
                ),
                ToolParameter(
                    name = "fire_and_check",
                    type = "boolean",
                    description = "True for fire-and-check loops (wake-up, state verification)",
                    required = false,
                ),
                ToolParameter(
                    name = "check_goal",
                    type = "string",
                    description = "What to verify later (e.g. user is awake, still at desk)",
                    required = false,
                ),
                ToolParameter(
                    name = "trigger_reason",
                    type = "string",
                    description = "User phrase or reason that started this fire-and-check",
                    required = false,
                ),
            ),
            returns = "success, reminder_id, scheduled_time",
            example = """{"name": "set_reminder", "params": {"message": "Prendi le medicine", "delay_minutes": 10}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        Log.i(TAG, "execute params=${invocation.params}")

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

        return try {
            val taskId = repository.schedule(message, triggerMillis)
            ScheduledTaskAlarmScheduler.schedule(context, taskId, triggerMillis)
            val scheduledTime = repository.formatScheduledTime(triggerMillis)
            Log.i(TAG, "Scheduled task id=$taskId at $scheduledTime: $message")

            trackFireAndCheck(
                reminderId = taskId,
                message = message,
                triggerMillis = triggerMillis,
                params = invocation.params,
            )
            memoryWriter.onReminderScheduled(
                taskId = taskId,
                message = message,
                triggerAtMillis = triggerMillis,
            )

            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "message" to message,
                    "scheduled_time" to scheduledTime,
                    "reminder_id" to taskId,
                ),
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

    private suspend fun trackFireAndCheck(
        reminderId: Long,
        message: String,
        triggerMillis: Long,
        params: Map<String, Any?>,
    ) {
        if (FireAndCheckRepository.isVerificationMessage(message)) {
            fireAndCheckRepository.onVerificationReminderScheduled(
                verificationReminderId = reminderId,
                verificationMessage = message,
                verificationDueAtMillis = triggerMillis,
            )
            return
        }

        fireAndCheckRepository.onPrimaryReminderScheduled(
            reminderId = reminderId,
            primaryMessage = message,
            primaryDueAtMillis = triggerMillis,
            checkGoal = params["check_goal"]?.toString(),
            triggerReason = params["trigger_reason"]?.toString(),
            fireAndCheck = parseBoolean(params["fire_and_check"]),
        )
    }

    private fun parseBoolean(raw: Any?): Boolean =
        when (raw) {
            is Boolean -> raw
            is String -> raw.equals("true", ignoreCase = true)
            else -> false
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
}
