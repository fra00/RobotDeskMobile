package com.example.mydeskrobot.domain.translation

import com.example.mydeskrobot.reasoning.model.TranslationModeState

/**
 * Pure Kotlin rules for continuous translation mode.
 */
object TranslationModePolicy {

    /**
     * TTS should use the target locale only while mode stays active across the turn
     * (not on the enter-ack or exit-confirm turns).
     */
    fun ttsLanguageTagForTurn(
        modeAtTurnStart: TranslationModeState,
        modeAfterTools: TranslationModeState,
    ): String? {
        if (modeAtTurnStart.enabled && modeAfterTools.enabled) {
            val tag = modeAfterTools.targetLanguageTag.trim()
            return tag.takeIf { it.isNotEmpty() }
        }
        return null
    }

    fun buildPromptSection(state: TranslationModeState): String {
        if (!state.enabled) return ""
        val label = state.targetLanguageLabel.ifBlank { state.targetLanguageTag }.ifBlank { "the target language" }
        val tag = state.targetLanguageTag.ifBlank { "en-US" }
        return buildString {
            appendLine("ACTIVE TRANSLATION MODE (overrides the default Italian reply rule):")
            appendLine("- Target language: $label (TTS locale hint: $tag).")
            appendLine("- For EVERY user utterance that is NOT an exit request: reply with ONLY the translation into $label.")
            appendLine("- No commentary, no Italian framing, no tools except set_translation_mode to exit.")
            appendLine("- Do not chat as desk companion while this mode is active.")
            appendLine("- If the user asks to stop translating (ferma la traduzione, stop con le traduzioni, basta tradurre, torna normale rispetto alle traduzioni): call set_translation_mode with enabled=false, then confirm briefly in Italian.")
            appendLine("- One-shot requests like \"come si dice X\" while mode is off are normal dialogue — this block applies only when mode is active.")
        }.trim()
    }
}
