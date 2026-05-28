package com.example.mydeskrobot.data.llm

import com.example.mydeskrobot.domain.llm.LlmEmotionMapper
import com.example.mydeskrobot.domain.model.LlmAssistantReply
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Interpreta la risposta grezza del modello: JSON strutturato o testo semplice (fallback).
 */
class LlmResponseParser(
    moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
) {

    private val adapter = moshi.adapter(LlmReplyJson::class.java)

    fun parse(raw: String): LlmAssistantReply {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Empty LLM response")
        }

        val jsonPayload = extractJsonPayload(trimmed)
        if (jsonPayload != null) {
            runCatching { adapter.fromJson(jsonPayload) }.getOrNull()?.let { json ->
                return LlmAssistantReply(
                    text = json.spokenText(),
                    emotion = LlmEmotionMapper.fromLlmValue(json.emotion),
                    imageRequired = json.needsImage(),
                )
            }
        }

        return LlmAssistantReply(text = trimmed, emotion = null, imageRequired = false)
    }

    private fun extractJsonPayload(raw: String): String? {
        val fence = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
        fence.find(raw)?.groupValues?.getOrNull(1)?.trim()?.let { if (it.startsWith("{")) return it }

        if (raw.startsWith("{") && raw.endsWith("}")) return raw

        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1)
        }
        return null
    }
}
