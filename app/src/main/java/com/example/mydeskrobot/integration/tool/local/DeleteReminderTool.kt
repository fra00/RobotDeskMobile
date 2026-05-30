package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.data.scheduled.ScheduledTaskAlarmScheduler
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class DeleteReminderTool(
    private val context: Context,
    private val repository: ScheduledTaskRepository = ScheduledTaskRepository.create(context),
) : Tool {

    override val name: String = "delete_reminder"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Cancel a pending reminder by id from get_reminders.",
            parameters = listOf(
                ToolParameter(
                    name = "task_id",
                    type = "integer",
                    description = "Reminder id to cancel",
                    required = true,
                ),
            ),
            returns = "success (boolean)",
            example = """{"name": "delete_reminder", "params": {"task_id": 1}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val taskId = (invocation.params["task_id"] as? Number)?.toLong()
            ?: (invocation.params["reminder_id"] as? Number)?.toLong()
            ?: return ToolResult.Error(
                message = "Parametro 'task_id' mancante",
                code = "MISSING_PARAM",
            )

        val cancelled = repository.cancel(taskId)
        if (!cancelled) {
            return ToolResult.Error(
                message = "Promemoria $taskId non trovato o già scaduto",
                code = "NOT_FOUND",
                recoverable = true,
            )
        }

        ScheduledTaskAlarmScheduler.cancel(context, taskId)
        return ToolResult.Success(
            data = mapOf(
                "success" to true,
                "task_id" to taskId,
            ),
        )
    }
}
