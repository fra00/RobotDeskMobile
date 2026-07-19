package com.example.mydeskrobot.memory.unified.embedding

import com.example.mydeskrobot.memory.unified.VectorMath

object EmbeddingQualityGate {

    /** Minimum raw cosine similarity on paraphrase gate pairs — see docs/MEMORY_EMBEDDING.md. */
    const val MIN_PARAPHRASE_COSINE = 0.55f

    data class GateResult(
        val passed: Boolean,
        val pairScores: List<Pair<MemoryEmbeddingGoldenSet.ParaphrasePair, Float>>,
        val failingPairs: List<MemoryEmbeddingGoldenSet.ParaphrasePair>,
    )

    suspend fun verifyParaphrasePairs(
        embedder: TextEmbedder,
        pairs: List<MemoryEmbeddingGoldenSet.ParaphrasePair> = MemoryEmbeddingGoldenSet.paraphraseGatePairs,
        minCosine: Float = MIN_PARAPHRASE_COSINE,
    ): GateResult {
        val scores = pairs.map { pair ->
            val memoryEmbedding = embedder.embed(pair.memory)
            val queryEmbedding = embedder.embed(pair.query)
            val cosine = if (memoryEmbedding != null && queryEmbedding != null) {
                VectorMath.cosineSimilarity(memoryEmbedding, queryEmbedding)
            } else {
                0f
            }
            pair to cosine
        }
        val failing = scores.filter { it.second < minCosine }.map { it.first }
        return GateResult(
            passed = failing.isEmpty(),
            pairScores = scores,
            failingPairs = failing,
        )
    }
}
