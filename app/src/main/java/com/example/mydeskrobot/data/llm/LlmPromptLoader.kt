package com.example.mydeskrobot.data.llm

import android.content.Context

/**
 * Carica i prompt LLM da file in [assets/prompts/].
 */
object LlmPromptLoader {

    const val SYSTEM_PROMPT_ASSET_PATH = "prompts/llm_system_prompt.txt"
    const val MEMORY_EXTRACTOR_PROMPT_ASSET_PATH = "prompts/memory_extractor_prompt.txt"

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
        return text
    }

    private fun loadTextAsset(context: Context, path: String): String =
        context.assets.open(path).use { input ->
            input.bufferedReader().readText()
        }.trim()
}
