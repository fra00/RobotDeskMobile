package com.example.mydeskrobot.integration.tool.hardware

import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.integration.body.BodyApiResult
import com.example.mydeskrobot.integration.body.BodyJoint
import com.example.mydeskrobot.integration.body.BodyStatus
import com.example.mydeskrobot.reasoning.model.ToolResult

internal object BodyToolSupport {

    fun parseSpeed(params: Map<String, Any?>): Int? {
        val raw = params["speed"] ?: return null
        return when (raw) {
            is Number -> raw.toInt().coerceIn(0, 100)
            is String -> raw.toIntOrNull()?.coerceIn(0, 100)
            else -> null
        }
    }

    fun parseDelta(params: Map<String, Any?>): Int? {
        val raw = params["delta"] ?: params["degrees"] ?: return null
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    fun parsePosition(params: Map<String, Any?>): Int? {
        val raw = params["position"] ?: return null
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    fun parseJoint(params: Map<String, Any?>): BodyJoint? {
        val name = (params["joint"] as? String)?.trim().orEmpty()
        if (name.isBlank()) return null
        return BodyJoint.fromApiName(name)
    }

    fun parseJointMap(params: Map<String, Any?>): Map<BodyJoint, Int> {
        val result = mutableMapOf<BodyJoint, Int>()
        BodyJoint.entries.forEach { joint ->
            val raw = params[joint.apiName] ?: return@forEach
            val value = when (raw) {
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull()
                else -> null
            } ?: return@forEach
            result[joint] = value
        }
        return result
    }

    fun <T> toToolResult(result: BodyApiResult<T>, mapSuccess: (T) -> Map<String, Any?>): ToolResult {
        return when (result) {
            is BodyApiResult.Success -> ToolResult.Success(data = mapSuccess(result.data))
            is BodyApiResult.Error -> ToolResult.Error(
                message = result.message,
                code = result.httpCode?.toString() ?: "BODY_ERROR",
                recoverable = true,
            )
        }
    }

    fun statusToMap(status: BodyStatus): Map<String, Any?> = mapOf(
        "moving" to status.moving,
        "joints" to status.joints.mapKeys { it.key }.mapValues { (_, state) ->
            mapOf(
                "position" to state.position,
                "target" to state.target,
                "min" to state.min,
                "max" to state.max,
            )
        },
        "ip" to status.ip,
        "hostname" to status.hostname,
        "url_ip" to status.urlIp,
        "rssi" to status.rssi,
    )

    fun notConfigured(): ToolResult.Error = ToolResult.Error(
        message = "Corpo robot non configurato. Imposta URL in Impostazioni → Corpo robot.",
        code = "BODY_NOT_CONFIGURED",
        recoverable = true,
    )
}

abstract class BodyTool(
    protected val client: BodyApiClient?,
) : com.example.mydeskrobot.integration.tool.Tool {
    override val locality = com.example.mydeskrobot.integration.tool.ToolLocality.HARDWARE

    protected fun requireClient(): BodyApiClient? = client
}
