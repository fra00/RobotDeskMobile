package com.example.mydeskrobot.integration.presence

import com.example.mydeskrobot.data.body.BodySettings
import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.integration.body.BodyApiResult

object BodyReachability {
    suspend fun isReachable(settings: BodySettings): Boolean {
        if (!settings.isConfigured()) return false
        val client = BodyApiClient.createIfConfigured(settings) ?: return false
        return client.getStatus() is BodyApiResult.Success
    }
}
