package com.example.mydeskrobot.data.proxy

import com.example.mydeskrobot.domain.proxy.PairingQrPayload
import org.json.JSONObject

object PairingQrCodec {

    const val DEFAULT_PORT = 8765
    const val PROTOCOL_VERSION = 1

    fun encode(payload: PairingQrPayload): String {
        return JSONObject()
            .put("v", payload.v)
            .put("name", payload.name)
            .put("ip", payload.ip)
            .put("port", payload.port)
            .put("token", payload.token)
            .put("robotId", payload.robotId)
            .toString()
    }

    fun decode(raw: String): PairingQrPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val json = when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("mydeskrobot://pair") -> parseDeepLink(trimmed)
                else -> return null
            }
            val ip = json.optString("ip").trim()
            val token = json.optString("token").trim()
            val robotId = json.optString("robotId").trim()
            val port = json.optInt("port", DEFAULT_PORT)
            if (ip.isEmpty() || token.isEmpty() || robotId.isEmpty() || port !in 1..65535) {
                return null
            }
            PairingQrPayload(
                v = json.optInt("v", PROTOCOL_VERSION),
                name = json.optString("name", "MyDeskRobot").ifBlank { "MyDeskRobot" },
                ip = ip,
                port = port,
                token = token,
                robotId = robotId,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDeepLink(uri: String): JSONObject {
        val query = uri.substringAfter('?', "")
        val map = query.split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null
                else part.substring(0, idx) to java.net.URLDecoder.decode(
                    part.substring(idx + 1),
                    Charsets.UTF_8.name(),
                )
            }
            .toMap()
        return JSONObject().apply {
            put("v", map["v"]?.toIntOrNull() ?: PROTOCOL_VERSION)
            put("name", map["name"] ?: "MyDeskRobot")
            put("ip", map["ip"] ?: "")
            put("port", map["port"]?.toIntOrNull() ?: DEFAULT_PORT)
            put("token", map["token"] ?: "")
            put("robotId", map["robotId"] ?: "")
        }
    }
}
