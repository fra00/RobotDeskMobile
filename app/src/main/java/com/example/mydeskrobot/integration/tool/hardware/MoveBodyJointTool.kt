package com.example.mydeskrobot.integration.tool.hardware

import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class MoveBodyJointTool(
    client: BodyApiClient?,
) : BodyTool(client) {

    override val name: String = "move_body_joint"

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "Move one physical robot joint (ESP32 myDeskBody). Silent gesture: do not narrate in reply. " +
            "base_pan=whole robot turns; display_pan=head/display swing without moving base; " +
            "head_tilt=nod up/down; head_roll=tilt sideways. Use delta for relative or position for absolute. " +
            "Combinable with take_photo and other tools in planner chains (see body capabilities prompt).",
        parameters = listOf(
            ToolParameter(
                name = "joint",
                type = "string",
                description = "base_pan (rotate entire robot L/R, default for guarda/gira) | " +
                    "display_pan (solo testa, senza muovere base) | head_tilt (su/giù, cenno sì) | head_roll (lato, cenno no)",
                required = true,
            ),
            ToolParameter(
                name = "delta",
                type = "integer",
                description = "Relative degrees (-45 to +45). Use delta OR position.",
                required = false,
            ),
            ToolParameter(
                name = "position",
                type = "integer",
                description = "Absolute degrees relative to home (-45 to +45). Good for scan angles (-25, 0, 25).",
                required = false,
            ),
            ToolParameter(
                name = "speed",
                type = "integer",
                description = "Optional speed 0-100 (0 = instant, omit = smooth default)",
                required = false,
            ),
        ),
        returns = "ok, joint, position, target",
        example = """{"name": "move_body_joint", "params": {"joint": "base_pan", "delta": -25}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val apiClient = requireClient() ?: return BodyToolSupport.notConfigured()
        val joint = BodyToolSupport.parseJoint(invocation.params)
            ?: return ToolResult.Error("Parametro 'joint' mancante o non valido", code = "MISSING_PARAM")
        val delta = BodyToolSupport.parseDelta(invocation.params)
        val position = BodyToolSupport.parsePosition(invocation.params)
        if (delta == null && position == null) {
            return ToolResult.Error("Serve 'delta' o 'position'", code = "MISSING_PARAM")
        }
        val speed = BodyToolSupport.parseSpeed(invocation.params)
        val result = apiClient.moveJoint(joint, delta = delta, position = position, speed = speed)
        return BodyToolSupport.toToolResult(result) { data ->
            mapOf(
                "ok" to true,
                "joint" to data.joint,
                "position" to data.position,
                "target" to data.target,
            )
        }
    }
}
