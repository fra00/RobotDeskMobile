package com.example.mydeskrobot.integration.memory

import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.reasoning.MemoryContextProvider

class MemoryPromptContextProviderImpl(
    private val memoryRepository: UserMemoryRepository,
) : MemoryContextProvider {

    override suspend fun buildContextFor(userText: String): String {
        val core = memoryRepository.getCoreIdentity(limit = 2)
        val relevant = memoryRepository.searchRelevant(userText, limit = 8)
            .filter { candidate -> core.none { it.id == candidate.id } }

        val selected = (core + relevant).take(10)
        if (selected.isEmpty()) return ""

        memoryRepository.markUsed(selected)

        val lines = selected.mapIndexed { index, item ->
            "${index + 1}. (${item.category}) ${item.value}"
        }
        return buildString {
            appendLine("KNOWN USER MEMORY (only use if relevant):")
            lines.forEach { appendLine(it) }
        }.trim()
    }
}
