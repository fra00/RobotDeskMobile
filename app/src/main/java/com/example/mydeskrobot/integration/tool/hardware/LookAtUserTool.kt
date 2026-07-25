package com.example.mydeskrobot.integration.tool.hardware

import com.example.mydeskrobot.integration.presence.AttentionCenteringResult
import com.example.mydeskrobot.integration.presence.UserAttentionCentering
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition

/**
 * On-request closed-loop body centering toward a face in the camera frame.
 */
class LookAtUserTool(
    private val attentionCentering: UserAttentionCentering,
) : Tool {

    override val name: String = "look_at_user"
    override val locality: ToolLocality = ToolLocality.HARDWARE

    override fun getDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = "Center the robot body toward a visible face (closed-loop ML Kit + pan/tilt). " +
            "Use when the user asks to look at them / center / guardami. " +
            "Not for 'turn left/right' or directional look — use move_body_joint from the current pose. " +
            "Targets the first face in frame only (no speaker identification).",
        parameters = emptyList(),
        returns = "centered | already_centered | skipped | error",
        example = """{"name": "look_at_user", "params": {}, "await_result": true}""",
    )

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        return when (val result = attentionCentering.tryCenterOnUser()) {
            AttentionCenteringResult.SkippedBodyDisabled ->
                BodyToolSupport.notConfigured()
            AttentionCenteringResult.SkippedPresenceDisabled ->
                ToolResult.Error(
                    message = "Presenza scrivania disabilitata. Abilitala in Impostazioni → Presenza.",
                    code = "PRESENCE_DISABLED",
                    recoverable = true,
                )
            AttentionCenteringResult.SkippedBodyUnreachable ->
                ToolResult.Error(
                    message = "Corpo robot non raggiungibile.",
                    code = "BODY_UNREACHABLE",
                    recoverable = true,
                )
            AttentionCenteringResult.AlreadyCentered ->
                ToolResult.Success(
                    data = mapOf(
                        "status" to "already_centered",
                        "message" to "Already facing the user",
                    ),
                )
            is AttentionCenteringResult.Centered ->
                ToolResult.Success(
                    data = mapOf(
                        "status" to "centered",
                        "moves" to result.moveCount,
                        "message" to "Centered toward face (${result.moveCount} move(s))",
                    ),
                )
            is AttentionCenteringResult.PartialFailure ->
                ToolResult.Error(
                    message = result.message,
                    code = "CENTERING_PARTIAL",
                    recoverable = true,
                )
        }
    }
}
