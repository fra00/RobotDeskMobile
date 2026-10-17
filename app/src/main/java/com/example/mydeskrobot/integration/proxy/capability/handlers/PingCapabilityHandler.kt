package com.example.mydeskrobot.integration.proxy.capability.handlers

import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityHandler
import org.json.JSONObject

class PingCapabilityHandler : PhoneCapabilityHandler {
    override suspend fun handle(params: Map<String, String>): JSONObject =
        JSONObject().put("pong", true).put("ts", System.currentTimeMillis())
}
