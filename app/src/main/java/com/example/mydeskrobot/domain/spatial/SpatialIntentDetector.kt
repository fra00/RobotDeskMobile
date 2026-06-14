package com.example.mydeskrobot.domain.spatial

/**
 * Lightweight Italian intent detection for spatial memory / room localization.
 */
object SpatialIntentDetector {

    enum class SpatialIntent {
        SCENE_CHANGED,
        LOCALIZE,
        MEMORIZE_PLACE,
        LIST_PLACES,
        GENERAL_SPATIAL,
        NONE,
    }

    data class DetectionResult(
        val intent: SpatialIntent,
        val shouldInvalidateCurrentPlace: Boolean,
        val userNamedPlace: String? = null,
    )

    private val INVALIDATE_PHRASES = listOf(
        "altra stanza",
        "un'altra stanza",
        "una nuova stanza",
        "nuova stanza",
        "cambiato stanza",
        "cambiata stanza",
        "scena cambiata",
        "ti ho spostato",
        "ti sposto",
        "non siamo più",
    )

    private val LOCALIZE_PHRASES = listOf(
        "dove siamo",
        "che stanza",
        "quale stanza",
        "riconosci la stanza",
        "riconosci questa stanza",
        "in che stanza",
        "dove sono",
        "dove sei",
    )

    private val MEMORIZE_PHRASES = listOf(
        "memorizza questa stanza",
        "ricorda questa stanza",
        "salva questa stanza",
        "impara questa stanza",
        "questa è la",
        "questa e la",
    )

    private val SPATIAL_KEYWORDS = listOf(
        "stanza", "camera", "studio", "cucina", "soggiorno", "bagno",
        "guarda intorno", "panorama", "intorno", "ambiente",
        "luogo", "posto",
    )

    fun detect(userText: String): DetectionResult {
        val normalized = userText.trim().lowercase()
        if (normalized.isBlank()) {
            return DetectionResult(SpatialIntent.NONE, shouldInvalidateCurrentPlace = false)
        }

        if (INVALIDATE_PHRASES.any { normalized.contains(it) }) {
            return DetectionResult(
                intent = SpatialIntent.SCENE_CHANGED,
                shouldInvalidateCurrentPlace = true,
                userNamedPlace = extractNamedPlace(normalized),
            )
        }

        if (MEMORIZE_PHRASES.any { normalized.contains(it) }) {
            return DetectionResult(
                intent = SpatialIntent.MEMORIZE_PLACE,
                shouldInvalidateCurrentPlace = false,
                userNamedPlace = extractNamedPlace(normalized),
            )
        }

        if (LOCALIZE_PHRASES.any { normalized.contains(it) }) {
            return DetectionResult(
                intent = SpatialIntent.LOCALIZE,
                shouldInvalidateCurrentPlace = false,
            )
        }

        if (normalized.contains("elenca") && normalized.contains("stanz")) {
            return DetectionResult(SpatialIntent.LIST_PLACES, shouldInvalidateCurrentPlace = false)
        }

        if (SPATIAL_KEYWORDS.any { normalized.contains(it) }) {
            return DetectionResult(
                intent = SpatialIntent.GENERAL_SPATIAL,
                shouldInvalidateCurrentPlace = false,
                userNamedPlace = extractNamedPlace(normalized),
            )
        }

        return DetectionResult(SpatialIntent.NONE, shouldInvalidateCurrentPlace = false)
    }

    private fun extractNamedPlace(text: String): String? {
        val patterns = listOf(
            Regex("""(?:sono|siamo|sei|è|e)\s+(?:nella|nell'|nello|nel|in|l')\s*(?:la\s+)?(\p{L}+)"""),
            Regex("""questa\s+è\s+(?:la\s+)?(\p{L}+)"""),
            Regex("""questa\s+e\s+(?:la\s+)?(\p{L}+)"""),
            Regex("""stanza\s+(?:si\s+chiama\s+)?(\p{L}+)"""),
        )
        val skip = setOf("un", "una", "altra", "nuova", "questa", "quella", "la", "il")
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val name = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (name.isNotBlank() && name !in skip) return name
        }
        return null
    }
}
