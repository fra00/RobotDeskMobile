package com.example.mydeskrobot.reasoning.model

/**
 * Whether incoming notifications are forwarded to the LLM/TTS pipeline.
 */
enum class NotificationMode {
    /** Announce notifications as usual */
    NORMAL,

    /** DROP notifications (no LLM, TTS, or log) */
    SILENT,
}
