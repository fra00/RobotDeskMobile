package com.example.mydeskrobot.memory.extract

import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

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

    suspend fun processDelta(
        newEntries: List<ChatLogEntry>,
    ) {
        if (newEntries.isEmpty()) return

        val transcript = newEntries.joinToString("\n") { entry ->
            "${entry.role.uppercase()}: ${entry.text}"
        }

        val llmResult = llmClient.chat(
            messages = listOf(ConversationMessage.User(transcript)),
            systemPrompt = extractorPrompt,
        )
        val raw = llmResult.getOrNull()?.content?.trim().orEmpty()
        if (raw.isBlank()) return

        val payload = runCatching { adapter.fromJson(raw) }.getOrNull() ?: return
        payload.facts.forEach { fact ->
            val value = fact.value?.trim().orEmpty()
            if (value.isBlank()) return@forEach
            val category = parseCategory(fact.category) ?: MemoryCategory.FACT
            val confidence = (fact.confidence ?: 0.5f).coerceIn(0f, 1f)
            val sourceMessageId = newEntries.maxOf { it.id }
            memoryRepository.upsert(
                category = category,
                value = value,
                confidence = confidence,
                sourceMessageId = sourceMessageId,
            )
        }
        memoryRepository.pruneIfNeeded(maxMemoryItems)
    }

    private fun parseCategory(raw: String?): MemoryCategory? {
        val normalized = raw?.trim()?.uppercase().orEmpty()
        return runCatching { MemoryCategory.valueOf(normalized) }.getOrNull()
    }

    companion object {
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
    }
}
