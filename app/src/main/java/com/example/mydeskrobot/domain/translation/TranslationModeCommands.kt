package com.example.mydeskrobot.domain.translation

import com.example.mydeskrobot.reasoning.model.TranslationModeState

/**
 * Parses [set_translation_mode] tool params into a state (no Android).
 */
object TranslationModeCommands {

    fun parse(params: Map<String, Any?>): TranslationModeState {
        val enabled = parseEnabled(params["enabled"])
            ?: throw IllegalArgumentException("enabled is required (true|false)")

        if (!enabled) return TranslationModeState.OFF

        val resolved = TranslationLanguageMapper.resolve(params["target_language"]?.toString())
            ?: throw IllegalArgumentException("target_language is required when enabling translation mode")

        return TranslationModeState(
            enabled = true,
            targetLanguageTag = resolved.languageTag,
            targetLanguageLabel = resolved.label,
        )
    }

    private fun parseEnabled(raw: Any?): Boolean? {
        return when (raw) {
            is Boolean -> raw
            null -> null
            else -> when (raw.toString().trim().lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> null
            }
        }
    }
}
