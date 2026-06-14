package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class LogDailyActivityTool(
    private val activityLogRepository: ActivityLogRepository,
) : Tool {

    constructor(context: Context) : this(ActivityLogRepository.create(context))

    override val name: String = "log_daily_activity"

    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Log a short-lived daily activity (meal, walk, break, outing). Not for durable facts or reminders.",
            parameters = listOf(
                ToolParameter(
                    name = "activity",
                    type = "string",
                    description = "Short normalized activity label in Italian (e.g. \"colazione\", \"passeggiata\")",
                    required = true,
                ),
                ToolParameter(
                    name = "note",
                    type = "string",
                    description = "Optional extra detail from the user phrase",
                    required = false,
                ),
            ),
            returns = "activity_id (integer), label",
            example = """{"name": "log_daily_activity", "params": {"activity": "colazione", "note": "prima del lavoro"}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val activity = (invocation.params["activity"] as? String)?.trim().orEmpty()
        if (activity.isBlank()) {
            return ToolResult.Error(
                message = "Parametro activity obbligatorio",
                code = "MISSING_ACTIVITY",
                recoverable = true,
            )
        }
        val note = (invocation.params["note"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val id = activityLogRepository.appendEvent(
            label = activity,
            rawPhrase = note,
            source = ActivitySource.TOOL,
        )
        if (id < 0L) {
            return ToolResult.Error(
                message = "Impossibile registrare l'attività",
                code = "LOG_FAILED",
                recoverable = true,
            )
        }
        return ToolResult.Success(
            data = mapOf(
                "activity_id" to id,
                "label" to ActivityLogRepository.normalizeLabel(activity),
            ),
        )
    }
}
