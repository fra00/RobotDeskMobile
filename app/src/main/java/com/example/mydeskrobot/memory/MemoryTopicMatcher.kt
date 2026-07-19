package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.db.MemoryItemEntity

/**
 * Ranks stored memories against a natural-language topic (forget/search).
 * Does not require the user phrase to match stored text exactly.
 */
object MemoryTopicMatcher {

    const val MIN_RANK_SCORE = 0.25f
    const val MIN_FORGET_SCORE = 0.3f

    private val STOP_WORDS = setOf(
        "il", "lo", "la", "i", "gli", "le", "un", "una", "uno", "di", "a", "da", "in", "con", "su", "per",
        "che", "non", "mi", "mio", "mia", "miei", "mie", "tuo", "tua", "del", "della", "dei", "delle",
        "al", "alla", "ai", "alle", "dal", "dalla", "nel", "nella", "sul", "sulla", "e", "o", "ma",
        "memoria", "memorie", "dimentica", "dimenticare", "rimuovi", "rimuovere", "cancella", "cancello",
        "cancellare", "elimina", "eliminare", "forget", "ricorda", "ricordati", "ricordami", "tutto", "quello", "cosa",
        "come", "sono", "ho", "hai", "avevo", "detto", "sulla", "sulle", "degli", "delle",
    )

    /** Italian paraphrase groups for identity/name recall (chiamo ↔ si chiama ↔ nome). */
    private val RELATED_TOKEN_GROUPS = listOf(
        setOf("chiamo", "chiami", "chiama", "chiam", "chiamato", "chiamati", "chiamava", "nome", "nomi"),
        setOf("identita", "identità", "ident"),
    )

    data class ScoredMemory(
        val item: MemoryItemEntity,
        val score: Float,
    )

    fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-zàèéìòù0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in STOP_WORDS }

    private fun tokensRelated(queryToken: String, memoryToken: String): Boolean {
        if (queryToken == memoryToken) return true
        if (queryToken.contains(memoryToken) || memoryToken.contains(queryToken)) return true
        val queryGroup = RELATED_TOKEN_GROUPS.firstOrNull { queryToken in it }
        val memoryGroup = RELATED_TOKEN_GROUPS.firstOrNull { memoryToken in it }
        return queryGroup != null && queryGroup === memoryGroup
    }

    private fun queryTokenMatchesMemory(queryToken: String, memoryValue: String, memoryTokens: List<String>): Boolean {
        if (queryToken.length >= 2 && memoryValue.contains(queryToken)) return true
        return memoryTokens.any { memoryToken -> tokensRelated(queryToken, memoryToken) }
    }

    fun score(query: String, memoryValue: String): Float {
        val q = query.trim().lowercase()
        val m = memoryValue.trim().lowercase()
        if (q.isBlank() || m.isBlank()) return 0f
        if (m.contains(q)) return 1f

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) {
            return if (m.contains(q)) 1f else 0f
        }

        val memoryTokens = tokenize(memoryValue)
        var matched = 0
        for (qt in queryTokens) {
            if (queryTokenMatchesMemory(qt, m, memoryTokens)) matched++
        }
        val tokenScore = matched.toFloat() / queryTokens.size

        var reverse = 0
        for (mt in memoryTokens) {
            if (mt.length >= 3 && queryTokens.any { qt -> tokensRelated(qt, mt) }) {
                reverse++
            }
        }
        val reverseScore = if (memoryTokens.isEmpty()) 0f else reverse.toFloat() / memoryTokens.size

        return (tokenScore * 0.75f + reverseScore * 0.25f).coerceIn(0f, 1f)
    }

    fun rank(query: String, items: List<MemoryItemEntity>, limit: Int = 50): List<ScoredMemory> =
        items
            .map { ScoredMemory(it, score(query, it.value)) }
            .filter { it.score >= MIN_RANK_SCORE }
            .sortedByDescending { it.score }
            .take(limit)

    /** Fallback when rank returns nothing: any significant token substring match. */
    fun fallbackMatches(query: String, items: List<MemoryItemEntity>): List<ScoredMemory> {
        val tokens = tokenize(query).filter { it.length >= 3 }
        if (tokens.isEmpty()) return emptyList()
        return items
            .map { item ->
                val hits = tokens.count { t -> item.value.lowercase().contains(t) }
                ScoredMemory(item, hits.toFloat() / tokens.size)
            }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
    }
}
