package com.example.mydeskrobot.integration.tool.hardware

import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition

class BodyStatusTool(
    client: BodyApiClient?,
) : BodyTool(client) {

    override val name: String = "body_status"

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "Read body state: joint position/target, moving flag, IP/RSSI. Use before long move sequences or when user asks motor positions.",
        parameters = emptyList(),
        returns = "moving, joints, ip, rssi",
        example = """{"name": "body_status", "params": {}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val apiClient = requireClient() ?: return BodyToolSupport.notConfigured()
        val result = apiClient.getStatus()
        return BodyToolSupport.toToolResult(result) { BodyToolSupport.statusToMap(it) }
    }
}
