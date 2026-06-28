package com.example.mydeskrobot.data.hotword

/**
 * Tracks whether the voice session (hotword listening) is active.
 * Used by heartbeat orchestrator to gate ticks before LLM work.
 */
object VoiceSessionState {
    @Volatile
    var isActive: Boolean = false
        private set

    fun setActive(active: Boolean) {
        isActive = active
    }
}
