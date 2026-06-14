package com.example.mydeskrobot.domain.telephony

/**
 * Expands informal contact names (e.g. "mamma") to rubrica-friendly variants (e.g. "madre").
 */
object ContactNameAliases {

    private val ALIAS_GROUPS = listOf(
        setOf("mamma", "madre", "mom", "mother"),
        setOf("papà", "papa", "padre", "babbo", "dad", "father"),
        setOf("nonna", "grandmother"),
        setOf("nonno", "grandfather"),
        setOf("marito", "sposo"),
        setOf("moglie", "sposa"),
        setOf("figlio", "figlia"),
        setOf("fratello", "sorella"),
    )

    fun normalize(text: String): String =
        text.trim().lowercase()
            .replace("à", "a")
            .replace("è", "e")
            .replace("é", "e")
            .replace("ì", "i")
            .replace("ò", "o")
            .replace("ù", "u")

    /**
     * Returns the query plus related aliases for contact lookup.
     */
    fun expandTerms(query: String): Set<String> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptySet()

        val terms = linkedSetOf(normalized)
        for (group in ALIAS_GROUPS) {
            val normalizedGroup = group.map { normalize(it) }.toSet()
            if (normalized in normalizedGroup || normalizedGroup.any { normalized.contains(it) }) {
                terms.addAll(normalizedGroup)
            }
        }
        return terms
    }
}
