package com.example.mydeskrobot.domain.spatial

object RoomLandmarks {

    private val SYNONYM_GROUPS = listOf(
        setOf("computer", "pc", "monitor", "schermo", "laptop", "portatile"),
        setOf("scrivania", "desk", "tavolo"),
        setOf("letto", "bed"),
        setOf("armadio", "wardrobe", "guardaroba"),
        setOf("comodino", "nightstand"),
        setOf("televisore", "tv", "television"),
        setOf("lampada", "lamp", "luce"),
        setOf("sedia", "chair", "poltrona"),
        setOf("attrezzi", "tools", "strumenti"),
        setOf("cucina", "fornello", "frigorifero", "frigo"),
        setOf("divano", "sofa"),
        setOf("lavandino", "sink"),
    )

    fun normalize(raw: String): String {
        val token = raw.trim().lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), "")
            .replace(Regex("""\s+"""), " ")
        if (token.isBlank()) return ""
        return canonical(token)
    }

    fun normalizeAll(items: Iterable<String>): List<String> =
        items.map(::normalize).filter { it.isNotBlank() }.distinct()

    fun canonical(token: String): String {
        val lower = token.lowercase()
        for (group in SYNONYM_GROUPS) {
            if (lower in group) return group.first()
        }
        return lower
    }

    fun merge(vararg sets: Iterable<String>): List<String> =
        normalizeAll(sets.flatMap { it })
}
