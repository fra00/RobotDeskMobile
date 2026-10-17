package com.example.mydeskrobot.integration.tool.local

import android.util.Log
import com.example.mydeskrobot.data.translation.TranslationModeRepository
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.model.TranslationModeState
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

/**
 * Enables or disables continuous translation mode (user speaks → robot replies only with translation).
 */
class SetTranslationModeTool(
    private val repository: TranslationModeRepository,
) : Tool {

    override val name: String = "set_translation_mode"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Enable or disable continuous translation mode. " +
                "When enabled, every following user utterance is translated into target_language until disabled. " +
                "Use for \"da adesso traduci in …\" / \"ferma la traduzione\". " +
                "Do NOT use for one-shot \"come si dice X\" or \"traduci questa frase\" — answer those in dialogue.",
            parameters = listOf(
                ToolParameter(
                    name = "enabled",
                    type = "boolean",
                    description = "true to start continuous translation; false to stop and return to normal companion",
                    required = true,
                ),
                ToolParameter(
                    name = "target_language",
                    type = "string",
                    description = "Required when enabled=true: language name or code (e.g. inglese, English, en, francese)",
                    required = false,
                ),
            ),
            returns = "enabled, target_language, target_language_tag, summary",
            example = """{"name": "set_translation_mode", "params": {"enabled": true, "target_language": "inglese"}, "await_result": false}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        return try {
            val state = repository.applyFromToolParams(invocation.params)
            val summary = buildSummary(state)
            Log.i(TAG, summary)
            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "enabled" to state.enabled,
                    "target_language" to state.targetLanguageLabel,
                    "target_language_tag" to state.targetLanguageTag,
                    "summary" to summary,
                ),
            )
        } catch (e: IllegalArgumentException) {
            ToolResult.Error(e.message ?: "Invalid set_translation_mode params")
        }
    }

    private fun buildSummary(state: TranslationModeState): String {
        return if (state.enabled) {
            "Modalità traduzione attiva: ${state.targetLanguageLabel}."
        } else {
            "Modalità traduzione disattivata."
        }
    }

    companion object {
        private const val TAG = "SetTranslationModeTool"
    }
}
