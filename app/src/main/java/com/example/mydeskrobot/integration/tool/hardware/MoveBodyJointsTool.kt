package com.example.mydeskrobot.integration.tool.hardware

import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class MoveBodyJointsTool(
    client: BodyApiClient?,
) : BodyTool(client) {

    override val name: String = "move_body_joints"

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "Move multiple physical joints simultaneously (ESP32). Use for combined poses. " +
            "Also combinable with take_photo and other tools; move_body_joint chains work well for stepwise gestures.",
        parameters = listOf(
            ToolParameter(name = "base_pan", type = "integer", description = "Target degrees for base (whole robot)", required = false),
            ToolParameter(name = "head_roll", type = "integer", description = "Target degrees for lateral head tilt", required = false),
            ToolParameter(name = "head_tilt", type = "integer", description = "Target degrees for nod up/down", required = false),
            ToolParameter(name = "display_pan", type = "integer", description = "Target degrees for display swing on neck", required = false),
            ToolParameter(name = "speed", type = "integer", description = "Optional speed 0-100 for all joints in this command", required = false),
        ),
        returns = "ok",
        example = """{"name": "move_body_joints", "params": {"base_pan": 15, "head_tilt": -10, "speed": 40}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val apiClient = requireClient() ?: return BodyToolSupport.notConfigured()
        val joints = BodyToolSupport.parseJointMap(invocation.params)
        if (joints.isEmpty()) {
            return ToolResult.Error("Specifica almeno un joint", code = "MISSING_PARAM")
        }
        val speed = BodyToolSupport.parseSpeed(invocation.params)
        val result = apiClient.moveJoints(joints, speed)
        return BodyToolSupport.toToolResult(result) { mapOf("ok" to true, "message" to it.message) }
    }
}
