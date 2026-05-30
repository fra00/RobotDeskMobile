package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition

class GetRemindersTool(
    private val repository: ScheduledTaskRepository,
) : Tool {

    constructor(context: Context) : this(ScheduledTaskRepository.create(context))

    override val name: String = "get_reminders"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "List active pending reminders scheduled by the user.",
            parameters = emptyList(),
            returns = "reminders (array of id, message, scheduled_time)",
            example = """{"name": "get_reminders", "params": {}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val pending = repository.listPending()
        val reminders = pending.map { task ->
            mapOf(
                "id" to task.id,
                "message" to task.message,
                "scheduled_time" to repository.formatScheduledTime(task.triggerAtMillis),
            )
        }
        return ToolResult.Success(
            data = mapOf(
                "count" to reminders.size,
                "reminders" to reminders,
            ),
        )
    }
}
