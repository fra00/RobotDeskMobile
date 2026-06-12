package com.example.mydeskrobot.data.llm

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Carica i prompt LLM da file in [assets/prompts/].
 */
object LlmPromptLoader {

    const val SYSTEM_PROMPT_ASSET_PATH = "prompts/llm_system_prompt.txt"
    const val MEMORY_EXTRACTOR_PROMPT_ASSET_PATH = "prompts/memory_extractor_prompt.txt"
    const val PRESENCE_DETECTION_PROMPT_ASSET_PATH = "prompts/presence_detection_prompt.txt"
    const val BODY_CAPABILITIES_PROMPT_ASSET_PATH = "prompts/body_capabilities_prompt.txt"

    private const val DATETIME_PLACEHOLDER = "{{CURRENT_DATETIME}}"

    fun loadSystemPrompt(context: Context): String {
        val text = loadTextAsset(context, SYSTEM_PROMPT_ASSET_PATH)

        require(text.isNotBlank()) {
            "System prompt asset is empty: $SYSTEM_PROMPT_ASSET_PATH"
        }

        return text
    }

    fun loadMemoryExtractorPrompt(context: Context): String {
        val text = loadTextAsset(context, MEMORY_EXTRACTOR_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Memory extractor prompt asset is empty: $MEMORY_EXTRACTOR_PROMPT_ASSET_PATH"
        }
        return text.replace(DATETIME_PLACEHOLDER, getCurrentDateTimeString())
    }

    private fun getCurrentDateTimeString(): String {
        val dateFormat = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN)
        return dateFormat.format(Date())
    }

    fun loadBodyCapabilitiesPrompt(context: Context): String {
        val text = loadTextAsset(context, BODY_CAPABILITIES_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Body capabilities prompt asset is empty: $BODY_CAPABILITIES_PROMPT_ASSET_PATH"
        }
        return text
    }

    fun loadPresenceDetectionPrompt(context: Context): String {
        val text = loadTextAsset(context, PRESENCE_DETECTION_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Presence detection prompt asset is empty: $PRESENCE_DETECTION_PROMPT_ASSET_PATH"
        }
        return text
    }

    private fun loadTextAsset(context: Context, path: String): String =
        context.assets.open(path).use { input ->
            input.bufferedReader().readText()
        }.trim()
}
