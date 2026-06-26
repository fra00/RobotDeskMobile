package com.example.mydeskrobot.integration.whatsapp

import com.example.mydeskrobot.domain.messaging.WhatsAppUriBuilder
import com.example.mydeskrobot.domain.telephony.ContactNameMatcher
import com.example.mydeskrobot.domain.telephony.PhoneNumberExtractor
import com.example.mydeskrobot.integration.telephony.PhoneContactResolver
import com.example.mydeskrobot.integration.telephony.PhoneContactResolveResult
import com.example.mydeskrobot.memory.MemoryTopicMatcher
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository

/**
 * Resolves a spoken contact or group name to a WhatsApp send target (phone digits or group id).
 */
class WhatsAppTargetResolver(
    private val whatsAppContactResolver: AndroidWhatsAppContactResolver,
    private val phoneContactResolver: PhoneContactResolver,
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
) {

    suspend fun resolve(query: String, preferGroup: Boolean = false): WhatsAppTargetResolveResult {
        val q = query.trim()
        if (q.isBlank()) return WhatsAppTargetResolveResult.InvalidQuery

        val waMatches = whatsAppContactResolver.search(q, preferGroup)
        val memoryMatches = searchMemory(q, preferGroup)
        val phoneAsContact = resolvePhoneContactAsWhatsApp(q, preferGroup)
        val merged = mergeMatches(waMatches + memoryMatches + phoneAsContact)

        if (merged.isEmpty()) {
            return if (!whatsAppContactResolver.hasPermission()) {
                WhatsAppTargetResolveResult.PermissionDenied
            } else {
                WhatsAppTargetResolveResult.NotFound(q)
            }
        }

        val best = merged.first()
        val second = merged.getOrNull(1)
        val clearlyBest = second == null || (best.score - second.score) >= AMBIGUITY_GAP

        return if (clearlyBest) {
            WhatsAppTargetResolveResult.Single(best)
        } else {
            WhatsAppTargetResolveResult.Multiple(merged.take(MAX_CANDIDATES))
        }
    }

    private suspend fun resolvePhoneContactAsWhatsApp(
        query: String,
        preferGroup: Boolean,
    ): List<WhatsAppTargetMatch> {
        if (preferGroup) return emptyList()
        return when (val result = phoneContactResolver.resolve(query)) {
            is PhoneContactResolveResult.Single -> listOf(
                WhatsAppTargetMatch(
                    displayName = result.match.displayName,
                    sendId = WhatsAppUriBuilder.normalizeSendId(result.match.number),
                    chatType = WhatsAppChatType.CONTACT,
                    source = when (result.match.source) {
                        com.example.mydeskrobot.integration.telephony.ContactPhoneSource.CONTACTS ->
                            WhatsAppTargetSource.CONTACTS
                        com.example.mydeskrobot.integration.telephony.ContactPhoneSource.MEMORY ->
                            WhatsAppTargetSource.MEMORY
                    },
                    score = result.match.score,
                ),
            )
            else -> emptyList()
        }
    }

    private suspend fun searchMemory(query: String, preferGroup: Boolean): List<WhatsAppTargetMatch> {
        val items = unifiedMemoryRepository.searchToolRelevant(
            query = query,
            limit = 12,
            includeRobotInternal = false,
        )
        val expanded = if (items.isEmpty()) {
            unifiedMemoryRepository.searchRelevantExpanded(query, limit = 8)
        } else {
            items
        }

        return expanded.mapNotNull { item ->
            val value = item.value
            val score = MemoryTopicMatcher.score(query, value)
            if (score < MemoryTopicMatcher.MIN_RANK_SCORE) return@mapNotNull null

            val groupJid = GROUP_JID.find(value)?.groupValues?.get(1)
            if (groupJid != null) {
                if (!preferGroup && !value.lowercase().contains("grupp") &&
                    !value.lowercase().contains("whatsapp")
                ) {
                    return@mapNotNull null
                }
                return@mapNotNull WhatsAppTargetMatch(
                    displayName = extractLabel(value, query),
                    sendId = groupJid,
                    chatType = WhatsAppChatType.GROUP,
                    source = WhatsAppTargetSource.MEMORY,
                    score = score,
                )
            }

            if (preferGroup) return@mapNotNull null

            val phone = PhoneNumberExtractor.extractFirst(value) ?: return@mapNotNull null
            val label = extractLabel(value, query)
            WhatsAppTargetMatch(
                displayName = label,
                sendId = WhatsAppUriBuilder.normalizeSendId(phone),
                chatType = WhatsAppChatType.CONTACT,
                source = WhatsAppTargetSource.MEMORY,
                score = maxOf(score, ContactNameMatcher.score(label, query)),
            )
        }
    }

    private fun extractLabel(memoryValue: String, query: String): String {
        val beforeColon = memoryValue.substringBefore(":").trim()
        if (beforeColon.isNotBlank() && beforeColon.length <= 60 && !beforeColon.any { it.isDigit() }) {
            return beforeColon
        }
        return query.trim()
    }

    private fun mergeMatches(matches: List<WhatsAppTargetMatch>): List<WhatsAppTargetMatch> {
        val byKey = linkedMapOf<String, WhatsAppTargetMatch>()
        for (match in matches.sortedByDescending { it.score }) {
            val key = "${match.chatType}|${match.sendId}"
            val existing = byKey[key]
            if (existing == null || match.score > existing.score) {
                byKey[key] = match
            }
        }
        return byKey.values.sortedByDescending { it.score }
    }

    companion object {
        private val GROUP_JID = Regex("""(\d{5,})@g\.us""")
        private const val AMBIGUITY_GAP = 0.15f
        private const val MAX_CANDIDATES = 5
    }
}
