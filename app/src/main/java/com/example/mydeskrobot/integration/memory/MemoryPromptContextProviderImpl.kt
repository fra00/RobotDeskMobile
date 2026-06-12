package com.example.mydeskrobot.integration.memory

import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.reasoning.MemoryContextProvider
import com.example.mydeskrobot.reasoning.memory.MemoryIntentDetector
import com.example.mydeskrobot.reasoning.memory.MemoryRetrievalProfile

class MemoryPromptContextProviderImpl(
    private val memoryRepository: UserMemoryRepository,
) : MemoryContextProvider {

    override suspend fun buildContextFor(
        userText: String,
        profileOverride: MemoryRetrievalProfile?,
    ): String {
        val detection = profileOverride?.let { MemoryIntentDetector.single(it) }
            ?: MemoryIntentDetector.detect(userText)

        val items = collectItems(userText, detection)
        if (items.isEmpty()) return ""

        memoryRepository.markUsed(items)

        val header = resolveHeader(detection)
        val lines = formatLines(items, detection)
        return buildString {
            appendLine(header)
            lines.forEach { appendLine(it) }
        }.trim()
    }

    private suspend fun collectItems(
        userText: String,
        detection: MemoryIntentDetector.DetectionResult,
    ): List<MemoryItemEntity> {
        val merged = linkedMapOf<Long, MemoryItemEntity>()

        fun addAll(items: List<MemoryItemEntity>) {
            items.forEach { merged.putIfAbsent(it.id, it) }
        }

        when (detection.primary) {
            MemoryRetrievalProfile.QUERY -> {
                addAll(memoryRepository.getCoreIdentity(limit = 2))
                addAll(memoryRepository.searchRelevantExpanded(userText, limit = 12))
            }
            MemoryRetrievalProfile.VISION -> {
                addAll(memoryRepository.getVisionCatalog(limit = 18))
            }
            MemoryRetrievalProfile.LEISURE -> {
                addAll(memoryRepository.getByCategories(listOf(MemoryCategory.PREFERENCE), limitPerCategory = 8))
                addAll(memoryRepository.searchRelevant(userText, limit = 4))
            }
            MemoryRetrievalProfile.PLAN -> {
                addAll(memoryRepository.getByCategories(listOf(MemoryCategory.ROUTINE), limitPerCategory = 6))
            }
            MemoryRetrievalProfile.DEFAULT -> {
                addAll(memoryRepository.getCoreIdentity(limit = 2))
                addAll(memoryRepository.searchRelevant(userText, limit = 8))
            }
        }

        if (detection.includes(MemoryRetrievalProfile.VISION) &&
            detection.primary != MemoryRetrievalProfile.VISION
        ) {
            addAll(memoryRepository.getVisionCatalog(limit = 18))
        }
        if (detection.includes(MemoryRetrievalProfile.QUERY) &&
            detection.primary != MemoryRetrievalProfile.QUERY
        ) {
            addAll(memoryRepository.getCoreIdentity(limit = 2))
            addAll(memoryRepository.searchRelevantExpanded(userText, limit = 12))
        }

        val maxItems = when (detection.primary) {
            MemoryRetrievalProfile.VISION -> 20
            MemoryRetrievalProfile.QUERY -> 14
            else -> 10
        }
        return merged.values.take(maxItems).toList()
    }

    private fun resolveHeader(detection: MemoryIntentDetector.DetectionResult): String {
        val hasVision = detection.includes(MemoryRetrievalProfile.VISION)
        val hasQuery = detection.includes(MemoryRetrievalProfile.QUERY)

        return when {
            hasVision && hasQuery ->
                "KNOWN ENTITIES FOR VISION (label what you see) + USER MEMORY (answer entity questions from values below):"
            detection.primary == MemoryRetrievalProfile.QUERY ->
                "KNOWN USER MEMORY (answer from these when user asks about an entity):"
            detection.primary == MemoryRetrievalProfile.VISION ->
                "KNOWN ENTITIES FOR VISION (label what you see using these names):"
            detection.primary == MemoryRetrievalProfile.LEISURE ->
                "USER PREFERENCES (suggest activities using these):"
            detection.primary == MemoryRetrievalProfile.PLAN ->
                "USER ROUTINES (combine with TODAY CONTEXT):"
            else ->
                "KNOWN USER MEMORY (use relevant facts in your reply):"
        }
    }

    private fun formatLines(
        items: List<MemoryItemEntity>,
        detection: MemoryIntentDetector.DetectionResult,
    ): List<String> {
        val useCompactVision = detection.includes(MemoryRetrievalProfile.VISION)
        return items.mapIndexed { index, item ->
            if (useCompactVision && item.category in setOf(MemoryCategory.FACT, MemoryCategory.ROUTINE)) {
                "${index + 1}. ${compactVisionLine(item.value)}"
            } else {
                "${index + 1}. (${item.category}) ${item.value}"
            }
        }
    }

    private fun compactVisionLine(value: String): String {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()
        val entityPrefixes = listOf(
            "l'utente ha un cane di nome " to "cane: ",
            "l'utente ha un cane " to "cane: ",
            "il cane dell'utente si chiama " to "cane: ",
            "il cane si chiama " to "cane: ",
            "l'utente ha un gatto di nome " to "gatto: ",
            "l'utente ha un gatto " to "gatto: ",
        )
        for ((prefix, label) in entityPrefixes) {
            if (lower.startsWith(prefix)) {
                return label + trimmed.substring(prefix.length).trimEnd('.')
            }
        }
        return trimmed
    }
}
