package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
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
            description = "Log a daily episodic event (activity, plan, social thread, commitment). Not for durable facts or fixed reminders.",
            parameters = listOf(
                ToolParameter(
                    name = "activity",
                    type = "string",
                    description = "Short normalized label in Italian (e.g. \"colazione\", \"cinema\")",
                    required = true,
                ),
                ToolParameter(
                    name = "note",
                    type = "string",
                    description = "Optional extra detail from the user phrase",
                    required = false,
                ),
                ToolParameter(
                    name = "kind",
                    type = "string",
                    description = "physical_now|plan|social_thread|commitment (default physical_now)",
                    required = false,
                ),
                ToolParameter(
                    name = "scheduled_day",
                    type = "string",
                    description = "Target day yyyy-MM-dd for future episodes",
                    required = false,
                ),
                ToolParameter(
                    name = "scheduled_time",
                    type = "string",
                    description = "Optional time HH:mm on scheduled_day",
                    required = false,
                ),
                ToolParameter(
                    name = "actor",
                    type = "string",
                    description = "Contact name for social_thread or plan",
                    required = false,
                ),
                ToolParameter(
                    name = "confidence",
                    type = "string",
                    description = "tentative|confirmed (default confirmed)",
                    required = false,
                ),
            ),
            returns = "activity_id (integer), label",
            example = """{"name": "log_daily_activity", "params": {"activity": "cinema", "kind": "plan", "scheduled_day": "2026-06-03", "scheduled_time": "20:30", "confidence": "confirmed"}, "await_result": true}""",
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
        val eventKind = parseKind(invocation.params["kind"] as? String)
        val confidence = parseConfidence(invocation.params["confidence"] as? String)
        val scheduledDayKey = (invocation.params["scheduled_day"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val scheduledAtMs = ActivityLogRepository.parseScheduledAtMs(
            scheduledDayKey = scheduledDayKey,
            scheduledTime = invocation.params["scheduled_time"] as? String,
        )
        val actor = invocation.params["actor"] as? String

        val id = if (eventKind == EpisodeKind.PHYSICAL_NOW) {
            activityLogRepository.appendEvent(
                label = activity,
                rawPhrase = note,
                source = ActivitySource.TOOL,
                eventKind = eventKind,
                confidence = confidence,
                scheduledAtMs = scheduledAtMs,
                scheduledDayKey = scheduledDayKey,
                actor = actor,
            )
        } else {
            activityLogRepository.upsertEpisodicEvent(
                label = activity,
                rawPhrase = note,
                source = ActivitySource.TOOL,
                eventKind = eventKind,
                confidence = confidence,
                scheduledAtMs = scheduledAtMs,
                scheduledDayKey = scheduledDayKey,
                actor = actor,
            )
        }
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

    private fun parseKind(raw: String?): EpisodeKind {
        return when (raw?.trim()?.lowercase()) {
            "plan" -> EpisodeKind.PLAN
            "social_thread" -> EpisodeKind.SOCIAL_THREAD
            "commitment" -> EpisodeKind.COMMITMENT
            else -> EpisodeKind.PHYSICAL_NOW
        }
    }

    private fun parseConfidence(raw: String?): EpisodeConfidence {
        return when (raw?.trim()?.lowercase()) {
            "tentative" -> EpisodeConfidence.TENTATIVE
            else -> EpisodeConfidence.CONFIRMED
        }
    }
}
