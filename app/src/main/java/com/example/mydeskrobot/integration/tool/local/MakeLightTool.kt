package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.data.light.DeskLightController
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

/**
 * Desk lamp mode: white bright UI + max screen brightness.
 */
class MakeLightTool : Tool {

    override val name: String = "make_light"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Turn desk-lamp mode on (white bright screen) or off (restore dark theme and previous brightness).",
            parameters = listOf(
                ToolParameter(
                    name = "on",
                    type = "boolean",
                    description = "true = fammi luce / accendi; false = spegni / torna normale",
                    required = true,
                ),
            ),
            returns = "bright_mode (boolean)",
            example = """{"name": "make_light", "params": {"on": true}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val on = parseOn(invocation.params["on"])
            ?: return ToolResult.Error(
                message = "Parametro on obbligatorio (true/false)",
                code = "MISSING_PARAM",
                recoverable = true,
            )
        DeskLightController.setBrightMode(on)
        return ToolResult.Success(
            data = mapOf(
                "bright_mode" to on,
            ),
        )
    }

    private fun parseOn(raw: Any?): Boolean? = when (raw) {
        is Boolean -> raw
        is String -> when (raw.trim().lowercase()) {
            "true", "1", "on", "yes", "si", "sì" -> true
            "false", "0", "off", "no" -> false
            else -> null
        }
        is Number -> raw.toInt() != 0
        else -> null
    }
}
