package com.example.mydeskrobot.memory.unified.embedding

/**
 * Golden queries and paraphrase pairs for Phase 0 embedding calibration.
 * See docs/MEMORY_EMBEDDING.md.
 */
object MemoryEmbeddingGoldenSet {

    data class ParaphrasePair(
        val memory: String,
        val query: String,
    )

    data class RetrievalSample(
        val memory: String,
        val query: String,
        val shouldMatch: Boolean,
    )

    /** Gate pairs: raw cosine similarity must be >= [EmbeddingQualityGate.MIN_PARAPHRASE_COSINE]. */
    val paraphraseGatePairs: List<ParaphrasePair> = listOf(
        ParaphrasePair(
            memory = "Il venerdì lavora dalle 9 alle 13",
            query = "quando lavoro il venerdì",
        ),
        ParaphrasePair(
            memory = "Il cane si chiama Brina",
            query = "come si chiama il mio animale",
        ),
        ParaphrasePair(
            memory = "Ogni mattina fa colazione alle 8",
            query = "abitudini mattutine utente",
        ),
        ParaphrasePair(
            memory = "Lun-gio lavora anche il pomeriggio 14-18",
            query = "orari pomeridiani settimana",
        ),
        ParaphrasePair(
            memory = "L'utente fa smart working il martedì",
            query = "lavori da casa",
        ),
    )

    /** Hybrid retrieval calibration: positives should match, negatives should not. */
    val retrievalSamples: List<RetrievalSample> = buildList {
        paraphraseGatePairs.forEach { pair ->
            add(RetrievalSample(pair.memory, pair.query, shouldMatch = true))
        }
        add(
            RetrievalSample(
                memory = "Il cane si chiama Brina",
                query = "chi era Garibaldi",
                shouldMatch = false,
            ),
        )
        add(
            RetrievalSample(
                memory = "L'utente lavora con C# su TeamSystem",
                query = "chi era Garibaldi",
                shouldMatch = false,
            ),
        )
        add(
            RetrievalSample(
                memory = "La mamma si chiama Anna",
                query = "meteo domani",
                shouldMatch = false,
            ),
        )
        add(
            RetrievalSample(
                memory = "Promemoria: prendere le medicine alle 20",
                query = "orari di lavoro",
                shouldMatch = false,
            ),
        )
    }

    /** Token-only baseline queries from UNIFIED_MEMORY_RAG_PLAN §13 (subset). */
    val tokenBaselineQueries: List<Pair<String, String>> = listOf(
        "Il venerdì lavora dalle 9 alle 13" to "Quando lavoro il venerdì?",
        "L'utente fa smart working il martedì" to "Lavori da casa?",
        "Il cane si chiama Brina" to "Come si chiama il cane?",
        "Lista spesa: latte, pane" to "Cosa c'è nella lista della spesa?",
        "L'utente si chiama Francesco" to "Cosa sai di me?",
    )
}
