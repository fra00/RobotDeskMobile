package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import android.util.Log
import com.example.mydeskrobot.data.check.FireAndCheckRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskAlarmScheduler
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.domain.check.CommitmentFollowUpPolicy
import com.example.mydeskrobot.domain.check.FireAndCheckKind
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
            description = "Schedule a spoken reminder (delay_minutes or hour+minute), or pin an urgent " +
                "commitment with no time. With commitment_follow_up=true and a time: Kotlin adds " +
                "'Verifica impegno' +60 min. With commitment_follow_up=true and no time: sticky " +
                "icon until the user deletes it (do not invent an hour).",
            parameters = listOf(
                ToolParameter(
                    name = "message",
                    type = "string",
                    description = "Reminder / commitment message",
                    required = true,
                ),
                ToolParameter(
                    name = "delay_minutes",
                    type = "integer",
                    description = "Minutes from now (e.g. 'tra 10 minuti'). Omit for sticky commitment.",
                    required = false,
                ),
                ToolParameter(
                    name = "hour",
                    type = "integer",
                    description = "Hour 0-23 with minute for absolute time. Omit for sticky commitment.",
                    required = false,
                ),
                ToolParameter(
                    name = "minute",
                    type = "integer",
                    description = "Minute 0-59 with hour. Omit for sticky commitment.",
                    required = false,
                ),
                ToolParameter(
                    name = "fire_and_check",
                    type = "boolean",
                    description = "True for physical fire-and-check loops (wake-up, desk presence)",
                    required = false,
                ),
                ToolParameter(
                    name = "check_goal",
                    type = "string",
                    description = "What to verify later for physical check (e.g. user is awake)",
                    required = false,
                ),
                ToolParameter(
                    name = "trigger_reason",
                    type = "string",
                    description = "User phrase or reason that started this fire-and-check",
                    required = false,
                ),
                ToolParameter(
                    name = "commitment_follow_up",
                    type = "boolean",
                    description = "True when user urgency is clear (assolutamente / molto importante / …). " +
                        "With time: dialogic Verifica +60 min. Without time: sticky pin on the eyes UI.",
                    required = false,
                ),
                ToolParameter(
                    name = "commitment_goal",
                    type = "string",
                    description = "Short description of the commitment (sticky label or verify ask)",
                    required = false,
                ),
            ),
            returns = "success, sticky|reminder_id, scheduled_time, commitment_follow_up, verification_reminder_id",
            example = """{"name": "set_reminder", "params": {"message": "Invia il documento", "hour": 9, "minute": 0, "commitment_follow_up": true, "commitment_goal": "Inviare il documento"}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        Log.i(TAG, "execute params=${invocation.params}")

        val message = invocation.params["message"]?.toString()?.trim()
            ?: return ToolResult.Error(
                message = "Parametro 'message' mancante",
                code = "MISSING_PARAM",
            )

        val commitmentRequested = parseBoolean(invocation.params["commitment_follow_up"])
        val triggerMillis = computeTriggerMillis(invocation.params)

        if (triggerMillis == null) {
            if (!commitmentRequested) {
                return ToolResult.Error(
                    message = "Specifica 'delay_minutes' oppure 'hour' + 'minute'",
                    code = "MISSING_TIME",
                )
            }
            return createStickyCommitmentResult(message, invocation.params)
        }

        return try {
            var commitmentFollowUp = commitmentRequested
            var reRemindCount = 0
            if (commitmentFollowUp) {
                val slot = fireAndCheckRepository.prepareNewCommitmentFollowUp()
                if (slot == null) {
                    // Max re-remind already used — schedule plain reminder only.
                    commitmentFollowUp = false
                    Log.i(TAG, "commitment_follow_up capped — scheduling plain reminder")
                } else {
                    reRemindCount = slot
                }
            }

            val taskId = repository.schedule(message, triggerMillis)
            ScheduledTaskAlarmScheduler.schedule(context, taskId, triggerMillis)
            val scheduledTime = repository.formatScheduledTime(triggerMillis)
            Log.i(TAG, "Scheduled task id=$taskId at $scheduledTime: $message")

            var verificationReminderId: Long? = null
            if (commitmentFollowUp) {
                val goal = invocation.params["commitment_goal"]?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: message
                val verifyMessage = CommitmentFollowUpPolicy.buildVerificationMessage(goal)
                val verifyAt = CommitmentFollowUpPolicy.followUpDueAt(triggerMillis)
                verificationReminderId = repository.schedule(verifyMessage, verifyAt)
                ScheduledTaskAlarmScheduler.schedule(context, verificationReminderId, verifyAt)

                fireAndCheckRepository.onPrimaryReminderScheduled(
                    reminderId = taskId,
                    primaryMessage = message,
                    primaryDueAtMillis = triggerMillis,
                    checkGoal = goal,
                    triggerReason = invocation.params["trigger_reason"]?.toString()
                        ?: message,
                    fireAndCheck = true,
                    kind = FireAndCheckKind.COMMITMENT,
                    reRemindCount = reRemindCount,
                )
                fireAndCheckRepository.onVerificationReminderScheduled(
                    verificationReminderId = verificationReminderId,
                    verificationMessage = verifyMessage,
                    verificationDueAtMillis = verifyAt,
                )
                Log.i(
                    TAG,
                    "Commitment follow-up verify id=$verificationReminderId at " +
                        repository.formatScheduledTime(verifyAt),
                )
            } else {
                trackFireAndCheck(
                    reminderId = taskId,
                    message = message,
                    triggerMillis = triggerMillis,
                    params = invocation.params,
                )
            }

            memoryWriter.onReminderScheduled(
                taskId = taskId,
                message = message,
                triggerAtMillis = triggerMillis,
            )

            ToolResult.Success(
                data = buildMap {
                    put("success", true)
                    put("message", message)
                    put("scheduled_time", scheduledTime)
                    put("reminder_id", taskId)
                    put("commitment_follow_up", commitmentFollowUp)
                    if (verificationReminderId != null) {
                        put("verification_reminder_id", verificationReminderId)
                    }
                    if (commitmentRequested && !commitmentFollowUp) {
                        put("commitment_capped", true)
                    }
                },
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

    private suspend fun createStickyCommitmentResult(
        message: String,
        params: Map<String, Any?>,
    ): ToolResult {
        return try {
            val goal = params["commitment_goal"]?.toString()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: message
            val id = fireAndCheckRepository.createStickyCommitment(
                goal = goal,
                triggerReason = params["trigger_reason"]?.toString() ?: message,
                primaryMessage = message,
            )
            Log.i(TAG, "Sticky commitment id=$id goal=$goal")
            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "sticky" to true,
                    "commitment_follow_up" to true,
                    "fire_and_check_id" to id,
                    "message" to message,
                    "commitment_goal" to goal,
                ),
            )
        } catch (e: Exception) {
            ToolResult.Error(
                message = "Impossibile salvare l'impegno: ${e.message}",
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
            kind = FireAndCheckKind.PHYSICAL,
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
