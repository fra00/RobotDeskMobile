package com.example.mydeskrobot.domain.wellness

/**
 * Detects user utterances that ask for wellness / “come sto?” style status
 * (triggers on-demand DOMAIN_SCORE refresh before dialogue).
 */
object WellnessAskDetector {

    private val patterns: List<Regex> = listOf(
        Regex("""\bcome\s+sto\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcome\s+mi\s+sento\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcome\s+vado\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcome\s+vanno\s+(i\s+)?pasti\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcome\s+va\s+(il\s+)?(wellness|benessere)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bstato\s+wellness\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(il\s+)?wellness\b""", RegexOption.IGNORE_CASE),
        Regex("""\briepilogo\s+wellness\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcheck\s+wellness\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcome\s+sto\s+a\s+livello\b""", RegexOption.IGNORE_CASE),
    )

    fun isWellnessAsk(phrase: String): Boolean {
        val text = phrase.trim()
        if (text.isEmpty()) return false
        return patterns.any { it.containsMatchIn(text) }
    }
}
