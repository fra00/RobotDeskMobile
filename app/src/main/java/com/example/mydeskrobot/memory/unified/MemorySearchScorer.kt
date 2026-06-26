package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.MemoryTopicMatcher
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity

object MemorySearchScorer {

    /** Token-only fallback when ONNX model is absent. */
    const val TOKEN_ONLY_MIN_SCORE = 0.25f

    /** Phase 0 calibrated threshold when semantic + token hybrid is active. */
    const val HYBRID_MIN_SCORE = 0.40f

    const val DEFAULT_MIN_SCORE = TOKEN_ONLY_MIN_SCORE
    const val SEMANTIC_WEIGHT = 0.7f
    const val TOKEN_WEIGHT = 0.3f

    data class ScoredDocument(
        val document: MemoryDocumentEntity,
        val score: Float,
    )

    fun score(
        query: String,
        document: MemoryDocumentEntity,
        queryEmbedding: FloatArray? = null,
    ): Float {
        val tokenScore = MemoryTopicMatcher.score(query, document.value)
        val docEmbedding = EmbeddingCodec.decode(document.embedding)
        val semanticScore = if (queryEmbedding != null && docEmbedding != null) {
            VectorMath.cosineSimilarity(queryEmbedding, docEmbedding)
        } else {
            null
        }
        return when {
            semanticScore != null ->
                (SEMANTIC_WEIGHT * semanticScore + TOKEN_WEIGHT * tokenScore).coerceIn(0f, 1f)
            else -> tokenScore
        }
    }

    fun rank(
        query: String,
        documents: List<MemoryDocumentEntity>,
        limit: Int,
        minScore: Float = DEFAULT_MIN_SCORE,
        queryEmbedding: FloatArray? = null,
    ): List<ScoredDocument> {
        val effectiveMinScore = if (usesSemanticHybrid(queryEmbedding, documents)) {
            maxOf(minScore, HYBRID_MIN_SCORE)
        } else {
            minScore
        }
        return documents
            .map { doc -> ScoredDocument(doc, score(query, doc, queryEmbedding)) }
            .filter { it.score >= effectiveMinScore }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun usesSemanticHybrid(
        queryEmbedding: FloatArray?,
        documents: List<MemoryDocumentEntity>,
    ): Boolean {
        if (queryEmbedding == null) return false
        return documents.any { EmbeddingCodec.decode(it.embedding) != null }
    }
}
