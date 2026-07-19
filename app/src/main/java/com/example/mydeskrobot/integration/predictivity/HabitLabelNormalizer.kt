package com.example.mydeskrobot.integration.predictivity

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import org.json.JSONObject

class HabitLabelNormalizer(
    private val llmClient: LlmClient,
    private val normalizePrompt: String,
) {
    suspend fun normalize(distinctRawLabels: List<String>): Map<String, String> {
        if (distinctRawLabels.isEmpty()) return emptyMap()
        val identity = distinctRawLabels.associateWith { fallbackCanonical(it) }
        if (!llmClient.isConfigured()) return identity

        val inputJson = buildLabelsInputJson(distinctRawLabels)

        val response = llmClient.chat(
            messages = listOf(ConversationMessage.User(content = inputJson)),
            systemPrompt = normalizePrompt,
        ).getOrNull()?.content ?: return identity

        return parseMappings(response, distinctRawLabels, identity)
    }

    private fun buildLabelsInputJson(labels: List<String>): String {
        val encoded = labels.distinct().sorted().joinToString(separator = ",") { label ->
            "\"${label.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
        return """{"labels":[$encoded]}"""
    }

    private fun parseMappings(
        rawResponse: String,
        labels: List<String>,
        fallback: Map<String, String>,
    ): Map<String, String> {
        val jsonText = extractJsonObject(rawResponse) ?: return fallback
        return runCatching {
            val root = JSONObject(jsonText)
            val mappings = root.optJSONObject("mappings") ?: return fallback
            val result = fallback.toMutableMap()
            labels.forEach { label ->
                val canonical = mappings.optString(label).trim()
                if (canonical.isNotBlank()) {
                    result[label] = canonical
                }
            }
            result
        }.getOrDefault(fallback)
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun fallbackCanonical(label: String): String =
        ActivityLogRepository.normalizeLabel(label)
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifBlank { "activity" }

    companion object {
        fun displayLabelFromCanonical(canonical: String): String =
            canonical.replace('_', ' ')
                .trim()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
