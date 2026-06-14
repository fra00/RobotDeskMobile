package com.example.mydeskrobot.integration.telephony

import com.example.mydeskrobot.domain.telephony.ContactNameMatcher
import com.example.mydeskrobot.domain.telephony.PhoneNumberExtractor
import com.example.mydeskrobot.memory.MemoryTopicMatcher
import com.example.mydeskrobot.memory.UserMemoryRepository

/**
 * Resolves a spoken contact name to a phone number via rubrica and stored memories.
 */
class PhoneContactResolver(
    private val contactsResolver: AndroidContactsPhoneResolver,
    private val memoryRepository: UserMemoryRepository,
) {

    suspend fun resolve(query: String): PhoneContactResolveResult {
        val q = query.trim()
        if (q.isBlank()) return PhoneContactResolveResult.InvalidQuery

        val contactMatches = contactsResolver.search(q)
        val memoryMatches = searchMemory(q)
        val merged = mergeMatches(contactMatches + memoryMatches)

        if (merged.isEmpty()) {
            return if (!contactsResolver.hasPermission()) {
                PhoneContactResolveResult.PermissionDenied
            } else {
                PhoneContactResolveResult.NotFound(query)
            }
        }

        val best = merged.first()
        val second = merged.getOrNull(1)
        val clearlyBest = second == null || (best.score - second.score) >= AMBIGUITY_GAP

        return if (clearlyBest) {
            PhoneContactResolveResult.Single(best)
        } else {
            PhoneContactResolveResult.Multiple(merged.take(MAX_CANDIDATES))
        }
    }

    private suspend fun searchMemory(query: String): List<ContactPhoneMatch> {
        val items = memoryRepository.searchRelevant(query, limit = 12, includeRobotInternal = false)
        val fallback = if (items.isEmpty()) {
            memoryRepository.searchRelevantExpanded(query, limit = 8)
        } else {
            items
        }

        return fallback.mapNotNull { item ->
            val number = PhoneNumberExtractor.extractFirst(item.value) ?: return@mapNotNull null
            val label = extractContactLabel(item.value) ?: query
            val score = maxOf(
                MemoryTopicMatcher.score(query, item.value),
                ContactNameMatcher.score(label, query),
            )
            if (score < MemoryTopicMatcher.MIN_RANK_SCORE) return@mapNotNull null

            ContactPhoneMatch(
                displayName = label,
                number = number,
                source = ContactPhoneSource.MEMORY,
                score = score,
            )
        }
    }

    private fun extractContactLabel(memoryValue: String): String? {
        val beforeColon = memoryValue.substringBefore(":").trim()
        if (beforeColon.isNotBlank() && beforeColon.length <= 40 && !beforeColon.any { it.isDigit() }) {
            return beforeColon
        }
        val phoneIdx = memoryValue.indexOfFirst { it.isDigit() || it == '+' }
        if (phoneIdx > 0) {
            val prefix = memoryValue.substring(0, phoneIdx).trim()
            if (prefix.isNotBlank() && prefix.length <= 40) return prefix
        }
        return null
    }

    private fun mergeMatches(matches: List<ContactPhoneMatch>): List<ContactPhoneMatch> {
        val byNumber = linkedMapOf<String, ContactPhoneMatch>()
        for (match in matches.sortedByDescending { it.score }) {
            val existing = byNumber[match.number]
            if (existing == null || match.score > existing.score) {
                byNumber[match.number] = match
            }
        }
        return byNumber.values.sortedByDescending { it.score }
    }

    companion object {
        private const val AMBIGUITY_GAP = 0.15f
        private const val MAX_CANDIDATES = 5
    }
}

sealed class PhoneContactResolveResult {
    data class Single(val match: ContactPhoneMatch) : PhoneContactResolveResult()
    data class Multiple(val matches: List<ContactPhoneMatch>) : PhoneContactResolveResult()
    data class NotFound(val query: String) : PhoneContactResolveResult()
    data object PermissionDenied : PhoneContactResolveResult()
    data object InvalidQuery : PhoneContactResolveResult()
}
