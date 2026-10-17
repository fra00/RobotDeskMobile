package com.example.mydeskrobot.integration.predictivity

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.domain.predictivity.HabitCanonicalPolicy
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import org.json.JSONArray
import org.json.JSONObject

class HabitLabelNormalizer(
    private val llmClient: LlmClient,
    private val normalizePrompt: String,
) {
    suspend fun normalize(
        distinctRawLabels: List<String>,
        existingHabits: List<HabitNormalizeExisting> = emptyList(),
    ): Map<String, String> {
        if (distinctRawLabels.isEmpty()) return emptyMap()
        val identity = distinctRawLabels.associateWith { fallbackCanonical(it) }
        if (!llmClient.isConfigured()) {
            return identity.mapValues { HabitCanonicalPolicy.applyLocalAliases(it.value) }
        }

        val inputJson = buildInputJson(distinctRawLabels, existingHabits)
        val response = llmClient.chat(
            messages = listOf(ConversationMessage.User(content = inputJson)),
            systemPrompt = normalizePrompt,
        ).getOrNull()?.content ?: return identity.mapValues {
            HabitCanonicalPolicy.applyLocalAliases(it.value)
        }

        val parsed = parseMappings(response, distinctRawLabels, identity)
        return parsed.mapValues { (_, canonical) ->
            val cleaned = HabitCanonicalPolicy.applyLocalAliases(canonical)
            if (cleaned == "ignore_robot") cleaned else cleaned
        }
    }

    private fun buildInputJson(
        labels: List<String>,
        existingHabits: List<HabitNormalizeExisting>,
    ): String {
        val root = JSONObject()
        val labelsArray = JSONArray()
        labels.distinct().sorted().forEach { labelsArray.put(it) }
        root.put("labels", labelsArray)
        if (existingHabits.isNotEmpty()) {
            val habitsArray = JSONArray()
            existingHabits.forEach { habit ->
                habitsArray.put(
                    JSONObject()
                        .put("canonical", habit.canonicalLabel)
                        .put("display", habit.displayLabel)
                        .put("typical_time", formatClock(habit.typicalTimeMinutes)),
                )
            }
            root.put("existing_habits", habitsArray)
        }
        return root.toString()
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
        HabitCanonicalPolicy.applyLocalAliases(
            ActivityLogRepository.normalizeLabel(label)
                .lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), "_")
                .trim('_')
                .ifBlank { "activity" },
        )

    private fun formatClock(minutes: Int): String {
        val normalized = minutes.mod(24 * 60)
        return "%02d:%02d".format(normalized / 60, normalized % 60)
    }

    companion object {
        fun displayLabelFromCanonical(canonical: String): String =
            canonical.replace('_', ' ')
                .trim()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
