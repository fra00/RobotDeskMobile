package com.example.mydeskrobot.integration.tool.local

import android.util.Log
import com.example.mydeskrobot.data.context.RobotContextRepository
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.reasoning.model.RobotProfile
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

/**
 * Sets desk-robot interaction context (work, call, meeting) and notification handling.
 * Does NOT change phone DND — only how the robot reacts.
 */
class SetRobotContextTool(
    private val repository: RobotContextRepository,
) : Tool {

    override val name: String = "set_robot_context"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Set how the desk robot interacts: profile (work/call/meeting/focus), " +
                "silence robot notification announcements, duration or time window. " +
                "Use profile 'normal' to reset. Does NOT mute the phone.",
            parameters = listOf(
                ToolParameter(
                    name = "profile",
                    type = "string",
                    description = "normal | work | call | meeting | focus",
                    required = false,
                ),
                ToolParameter(
                    name = "notifications",
                    type = "string",
                    description = "Optional override: silent | normal",
                    required = false,
                ),
                ToolParameter(
                    name = "session_only",
                    type = "boolean",
                    description = "If true, context ends when the voice session ends",
                    required = false,
                ),
                ToolParameter(
                    name = "duration_minutes",
                    type = "integer",
                    description = "How long the context lasts (e.g. 60 for one hour)",
                    required = false,
                ),
                ToolParameter(
                    name = "until_hour",
                    type = "integer",
                    description = "End at this hour (24h, use with until_minute)",
                    required = false,
                ),
                ToolParameter(
                    name = "until_minute",
                    type = "integer",
                    description = "End at this minute (use with until_hour)",
                    required = false,
                ),
                ToolParameter(
                    name = "window_start_hour",
                    type = "integer",
                    description = "Daily window start hour (e.g. 12 for meeting)",
                    required = false,
                ),
                ToolParameter(
                    name = "window_start_minute",
                    type = "integer",
                    description = "Daily window start minute",
                    required = false,
                ),
                ToolParameter(
                    name = "window_end_hour",
                    type = "integer",
                    description = "Daily window end hour",
                    required = false,
                ),
                ToolParameter(
                    name = "window_end_minute",
                    type = "integer",
                    description = "Daily window end minute",
                    required = false,
                ),
            ),
            returns = "profile, notifications, summary",
            example = """{"name": "set_robot_context", "params": {"profile": "call", "duration_minutes": 60}, "await_result": false}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val effective = repository.applyFromToolParams(invocation.params)
        val summary = buildSummary(effective)
        Log.i(TAG, "Robot context updated: $summary")
        return ToolResult.Success(
            data = mapOf(
                "success" to true,
                "profile" to effective.profile.name.lowercase(),
                "notifications" to effective.notificationMode.name.lowercase(),
                "summary" to summary,
            ),
        )
    }

    private fun buildSummary(state: RobotContextState): String {
        if (state.isNormal || state.profile == RobotProfile.NORMAL) {
            return "Modalità normale ripristinata."
        }
        val profileIt = when (state.profile) {
            RobotProfile.WORK -> "lavoro"
            RobotProfile.CALL -> "chiamata"
            RobotProfile.MEETING -> "riunione"
            RobotProfile.FOCUS -> "focus"
            RobotProfile.NORMAL -> "normale"
        }
        val notif = if (state.notificationMode.name == "SILENT") {
            " Notifiche silenziate per il robot."
        } else {
            ""
        }
        val session = if (state.sessionOnly) " Valido per questa sessione." else ""
        return "Contesto impostato: $profileIt.$notif$session"
    }

    companion object {
        private const val TAG = "SetRobotContextTool"
    }
}
