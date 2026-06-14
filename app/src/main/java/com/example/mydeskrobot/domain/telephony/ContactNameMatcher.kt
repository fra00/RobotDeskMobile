package com.example.mydeskrobot.domain.telephony

/**
 * Scores how well a contact display name matches a spoken query (with alias support).
 */
object ContactNameMatcher {

    fun score(displayName: String, query: String): Float {
        val nameNorm = ContactNameAliases.normalize(displayName)
        val queryTerms = ContactNameAliases.expandTerms(query)
        if (nameNorm.isBlank() || queryTerms.isEmpty()) return 0f

        var best = 0f
        for (term in queryTerms) {
            if (term.isBlank()) continue
            val score = when {
                nameNorm == term -> 1f
                nameNorm.startsWith("$term ") || nameNorm.endsWith(" $term") -> 0.95f
                nameNorm.contains(" $term ") -> 0.9f
                nameNorm.contains(term) && term.length >= 3 -> 0.85f
                else -> tokenOverlapScore(nameNorm, term)
            }
            best = maxOf(best, score)
        }
        return best
    }

    private fun tokenOverlapScore(nameNorm: String, term: String): Float {
        val nameTokens = nameNorm.split(Regex("\\s+")).filter { it.length >= 2 }
        val termTokens = term.split(Regex("\\s+")).filter { it.length >= 2 }
        if (nameTokens.isEmpty() || termTokens.isEmpty()) return 0f

        var matched = 0
        for (tt in termTokens) {
            if (nameTokens.any { nt -> nt == tt || nt.startsWith(tt) || tt.startsWith(nt) }) {
                matched++
            }
        }
        return if (matched == 0) 0f else matched.toFloat() / termTokens.size * 0.8f
    }
}
