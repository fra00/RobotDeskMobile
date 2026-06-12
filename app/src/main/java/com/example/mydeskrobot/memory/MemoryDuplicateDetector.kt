package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.db.MemoryCategory

/**
 * Detects near-duplicate memory values (reworded Italian, IT/EN pairs, minor variations).
 */
object MemoryDuplicateDetector {

    private val USER_PREFIX = Regex(
        """^(l'utente\s+|the\s+user'?s?\s+|user\s+|utente\s+)""",
        RegexOption.IGNORE_CASE,
    )

    private const val TOPIC_MATCH_THRESHOLD = 0.62f
    private const val MIN_SHARED_TOKENS = 2
    private const val MIN_JACCARD = 0.34f
    private const val MIN_CONTAINMENT_LENGTH = 12

    fun normalizeForDedup(value: String): String =
        value.trim()
            .lowercase()
            .replace(USER_PREFIX, "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun areDuplicates(
        a: String,
        b: String,
        category: MemoryCategory,
    ): Boolean {
        val left = a.trim()
        val right = b.trim()
        if (left.isBlank() || right.isBlank()) return false
        if (left.equals(right, ignoreCase = true)) return true

        val normalizedLeft = normalizeForDedup(left)
        val normalizedRight = normalizeForDedup(right)
        if (normalizedLeft == normalizedRight) return true

        if (normalizedLeft.length >= MIN_CONTAINMENT_LENGTH &&
            normalizedRight.length >= MIN_CONTAINMENT_LENGTH &&
            (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft))
        ) {
            return true
        }

        if (MemoryTopicMatcher.score(left, right) >= TOPIC_MATCH_THRESHOLD) return true

        val tokensLeft = MemoryTopicMatcher.tokenize(left).toSet()
        val tokensRight = MemoryTopicMatcher.tokenize(right).toSet()
        if (tokensLeft.isEmpty() || tokensRight.isEmpty()) return false

        val shared = tokensLeft.intersect(tokensRight)
        if (shared.size >= MIN_SHARED_TOKENS && jaccard(tokensLeft, tokensRight) >= MIN_JACCARD) {
            return true
        }

        if (category == MemoryCategory.IDENTITY && shared.any { it.length >= 4 }) {
            return true
        }

        return false
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Float {
        val union = a.union(b).size
        if (union == 0) return 0f
        return a.intersect(b).size.toFloat() / union
    }
}
