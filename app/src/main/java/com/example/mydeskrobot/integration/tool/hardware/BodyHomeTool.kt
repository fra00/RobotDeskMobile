package com.example.mydeskrobot.integration.tool.hardware

import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class BodyHomeTool(
    client: BodyApiClient?,
) : BodyTool(client) {

    override val name: String = "body_home"

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "Reset all physical joints to neutral home (0°). Use after multi-angle scan or when user says torna neutro/posizione di riposo.",
        parameters = listOf(
            ToolParameter(
                name = "speed",
                type = "integer",
                description = "Optional speed 0-100 (0 = instant)",
                required = false,
            ),
        ),
        returns = "ok",
        example = """{"name": "body_home", "params": {"speed": 40}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val apiClient = requireClient() ?: return BodyToolSupport.notConfigured()
        val speed = BodyToolSupport.parseSpeed(invocation.params)
        val result = apiClient.home(speed)
        return BodyToolSupport.toToolResult(result) { mapOf("ok" to true, "message" to it.message) }
    }
}
