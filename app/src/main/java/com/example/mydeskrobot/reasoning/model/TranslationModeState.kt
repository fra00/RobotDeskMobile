package com.example.mydeskrobot.reasoning.model

/**
 * Persistent continuous translation mode (desk robot).
 * Orthogonal to [RobotContextState] (work/call/meeting).
 */
data class TranslationModeState(
    val enabled: Boolean = false,
    /** BCP-47 tag for TTS, e.g. en-US. */
    val targetLanguageTag: String = "",
    /** Human-readable label for prompts/UI, e.g. English / inglese. */
    val targetLanguageLabel: String = "",
) {
    companion object {
        val OFF = TranslationModeState()
    }
}
