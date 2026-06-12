package com.example.mydeskrobot.reasoning.memory

/**
 * Lightweight Italian intent detection for memory retrieval profiles.
 * Pure Kotlin — no Android dependencies.
 */
object MemoryIntentDetector {

    data class DetectionResult(
        val primary: MemoryRetrievalProfile,
        val secondary: Set<MemoryRetrievalProfile> = emptySet(),
    ) {
        val activeProfiles: Set<MemoryRetrievalProfile>
            get() = setOf(primary) + secondary

        fun includes(profile: MemoryRetrievalProfile): Boolean =
            profile in activeProfiles
    }

    private val VISION_KEYWORDS = listOf(
        "foto", "fotografia", "scatta", "guarda", "vedi", "riconosci",
        "camera", "intorno", "panorama", "nascondino", "immagine", "occhi",
        "cosa vedi", "fammi vedere",
    )

    private val PLAN_KEYWORDS = listOf(
        "devo fare", "cosa fare oggi", "programma", "agenda", "impegni",
        "riunione", "riunioni", "promemoria", "todo", "lista", "compiti",
        "appuntamento", "scadenza",
    )

    private val LEISURE_KEYWORDS = listOf(
        "cosa posso", "cosa guardare", "tempo libero", "mi consigli",
        "cosa fare stasera", "cosa vedere oggi", "cosa posso vedere",
        "cosa posso guardare", "suggerisci", "consigliami",
    )

    private val QUERY_KEYWORDS = listOf(
        "ricordi", "memoria", "memorie", "come si chiama", "come si chiama il",
        "del mio", "della mia", "il mio cane", "la mia", "parlami del",
        "parlami della", "dimmi del", "dimmi della", "sai del", "sai della",
        "controlla la memoria", "controlla memoria",
    )

    fun detect(userText: String): DetectionResult {
        val normalized = userText.trim().lowercase()
        if (normalized.isBlank()) {
            return DetectionResult(MemoryRetrievalProfile.DEFAULT)
        }

        val matched = mutableSetOf<MemoryRetrievalProfile>()

        if (containsAny(normalized, VISION_KEYWORDS)) {
            matched += MemoryRetrievalProfile.VISION
        }
        if (containsAny(normalized, PLAN_KEYWORDS)) {
            matched += MemoryRetrievalProfile.PLAN
        }
        if (containsAny(normalized, LEISURE_KEYWORDS) && !normalized.contains("devo fare")) {
            matched += MemoryRetrievalProfile.LEISURE
        }
        if (containsAny(normalized, QUERY_KEYWORDS)) {
            matched += MemoryRetrievalProfile.QUERY
        }

        if (matched.isEmpty()) {
            return DetectionResult(MemoryRetrievalProfile.DEFAULT)
        }

        val primary = resolvePrimary(normalized, matched)
        val secondary = matched - primary
        return DetectionResult(primary, secondary)
    }

    /**
     * Single profile when an override is requested (e.g. mid-chain VISION refresh).
     */
    fun single(profile: MemoryRetrievalProfile): DetectionResult =
        DetectionResult(profile)

    private fun resolvePrimary(
        normalized: String,
        matched: Set<MemoryRetrievalProfile>,
    ): MemoryRetrievalProfile {
        if (MemoryRetrievalProfile.QUERY in matched &&
            (MemoryRetrievalProfile.VISION !in matched || asksAboutEntity(normalized))
        ) {
            return MemoryRetrievalProfile.QUERY
        }
        if (MemoryRetrievalProfile.PLAN in matched) {
            return MemoryRetrievalProfile.PLAN
        }
        if (MemoryRetrievalProfile.LEISURE in matched) {
            return MemoryRetrievalProfile.LEISURE
        }
        if (MemoryRetrievalProfile.VISION in matched) {
            return MemoryRetrievalProfile.VISION
        }
        if (MemoryRetrievalProfile.QUERY in matched) {
            return MemoryRetrievalProfile.QUERY
        }
        return MemoryRetrievalProfile.DEFAULT
    }

    private fun asksAboutEntity(normalized: String): Boolean =
        normalized.contains("come si chiama") ||
            normalized.contains("ricordi") ||
            normalized.contains("memoria") ||
            normalized.contains("del mio") ||
            normalized.contains("della mia") ||
            normalized.contains("parlami del") ||
            normalized.contains("dimmi del") ||
            normalized.contains("controlla la memoria") ||
            normalized.contains("controlla memoria")

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { containsKeyword(text, it) }

    /**
     * Short tokens (e.g. "guarda") use word boundaries so "guardare" does not trigger VISION.
     */
    private fun containsKeyword(text: String, keyword: String): Boolean {
        if (keyword.length <= 5) {
            return Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(text)
        }
        return text.contains(keyword)
    }
}
