package com.example.mydeskrobot.integration.presence

import com.example.mydeskrobot.data.body.BodySettings

/**
 * Silent body scan to detect user presence at the desk (predictivity / wellness).
 */
class BodyLocateService(
    private val bodySettingsProvider: suspend () -> BodySettings,
    private val attentionCentering: UserAttentionCentering,
) {
    suspend fun locateUserNow(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
        val settings = bodySettingsProvider()
        if (!settings.isConfigured()) return false
        return attentionCentering.locateUserNow(timeoutMs)
    }

    suspend fun isBodyConfigured(): Boolean = bodySettingsProvider().isConfigured()

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}
