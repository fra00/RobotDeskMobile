package com.example.mydeskrobot.memory.extract

import android.util.Log
import com.example.mydeskrobot.domain.time.RelativeDateNormalizer
import com.example.mydeskrobot.integration.llm.LlmHttpErrors
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale

data class ChatLogEntry(
    val id: Long,
    val role: String,
    val text: String,
)

internal data class MemoryExtractionPayload(
    val facts: List<MemoryFactPayload> = emptyList(),
)

internal data class MemoryFactPayload(
    val category: String? = null,
    val value: String? = null,
    val confidence: Float? = null,
)

class MemoryExtractionService(
    private val llmClient: LlmClient,
    private val memoryRepository: UserMemoryRepository,
    private val extractorPrompt: String,
    private val maxMemoryItems: Int = 300,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(MemoryExtractionPayload::class.java)

    /**
     * @return number of facts persisted to Room
     */
    suspend fun processDelta(
        newEntries: List<ChatLogEntry>,
    ): Int {
        if (newEntries.isEmpty()) return 0

        val transcript = newEntries.joinToString("\n") { entry ->
            "${entry.role.uppercase()}: ${entry.text}"
        }

        val llmResult = llmClient.chat(
            messages = listOf(ConversationMessage.User(transcript)),
            systemPrompt = extractorPrompt,
        )
        llmResult.exceptionOrNull()?.let { error ->
            Log.w(TAG, "Memory extractor LLM failed: ${LlmHttpErrors.formatForLog(error)}", error)
            return 0
        }
        val raw = llmResult.getOrNull()?.content?.trim().orEmpty()
        if (raw.isBlank()) {
            Log.w(TAG, "Memory extractor returned empty content")
            return 0
        }

        val payload = parsePayload(raw)
        if (payload == null) {
            Log.w(TAG, "Memory extractor JSON parse failed: ${raw.take(120)}")
            return 0
        }
        if (payload.facts.isEmpty()) {
            Log.d(TAG, "Memory extractor returned no facts")
            return 0
        }

        var saved = 0
        val sourceMessageId = newEntries.maxOf { it.id }
        payload.facts.forEach { fact ->
            val rawValue = fact.value?.trim().orEmpty()
            if (rawValue.isBlank()) return@forEach
            if (isSkippableOneOffTask(rawValue)) {
                Log.d(TAG, "Skipping one-off task fact: ${rawValue.take(80)}")
                return@forEach
            }
            val value = RelativeDateNormalizer.normalize(rawValue)
            val category = parseCategory(fact.category) ?: MemoryCategory.FACT
            val confidence = (fact.confidence ?: 0.5f).coerceIn(0f, 1f)
            memoryRepository.upsert(
                category = category,
                value = value,
                confidence = confidence,
                sourceMessageId = sourceMessageId,
            )
            saved++
        }
        if (saved > 0) {
            memoryRepository.pruneIfNeeded(maxMemoryItems)
        }
        return saved
    }

    private fun parsePayload(raw: String): MemoryExtractionPayload? {
        val json = extractJsonBody(raw)
        return runCatching { adapter.fromJson(json) }.getOrNull()
    }

    private fun isSkippableOneOffTask(value: String): Boolean {
        val lower = value.lowercase(Locale.ITALIAN)
        return lower.contains("devo") && !lower.contains("ogni ")
    }

    private fun parseCategory(raw: String?): MemoryCategory? {
        val normalized = raw?.trim()?.uppercase().orEmpty()
        return runCatching { MemoryCategory.valueOf(normalized) }.getOrNull()
    }

    companion object {
        private const val TAG = "MemoryExtraction"

        fun extractEntriesFromConversationLog(conversationLog: String): List<ChatLogEntry> {
            if (conversationLog.isBlank()) return emptyList()
            val lines = conversationLog
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val entries = mutableListOf<ChatLogEntry>()
            var id = 1L
            lines.forEach { line ->
                when {
                    line.startsWith("Tu:", ignoreCase = true) -> {
                        entries.add(
                            ChatLogEntry(
                                id = id++,
                                role = "user",
                                text = line.removePrefix("Tu:").trim(),
                            ),
                        )
                    }
                    line.startsWith("Robot:", ignoreCase = true) -> {
                        entries.add(
                            ChatLogEntry(
                                id = id++,
                                role = "assistant",
                                text = line.removePrefix("Robot:").trim(),
                            ),
                        )
                    }
                }
            }
            return entries
        }

        internal fun extractJsonBody(raw: String): String {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("```")) return trimmed
            val withoutFence = trimmed
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .trim()
            return withoutFence.substringBeforeLast("```").trim()
        }
    }
}
