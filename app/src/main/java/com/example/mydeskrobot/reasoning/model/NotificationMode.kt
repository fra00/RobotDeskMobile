package com.example.mydeskrobot.reasoning.model

/**
 * Whether incoming notifications are forwarded to the LLM/TTS pipeline.
 */
enum class NotificationMode {
    /** Notifications processed normally including TTS when appropriate. */
    NORMAL,

    /** Process notifications (LLM + log); suppress spontaneous TTS until user asks. */
    SILENT,
}
