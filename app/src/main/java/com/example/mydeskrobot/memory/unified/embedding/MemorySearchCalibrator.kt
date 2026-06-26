package com.example.mydeskrobot.memory.unified.embedding

import com.example.mydeskrobot.memory.unified.MemorySearchScorer
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import kotlin.math.roundToInt

/**
 * Calibrates hybrid retrieval [minScore] from golden samples.
 */
object MemorySearchCalibrator {

    data class CalibrationResult(
        val recommendedMinScore: Float,
        val positiveRecall: Float,
        val negativeRejectRate: Float,
        val embedLatencyMedianMs: Long,
        val embedLatencyP95Ms: Long,
        val scoresAtThreshold: List<SampleScore>,
    )

    data class SampleScore(
        val memory: String,
        val query: String,
        val shouldMatch: Boolean,
        val hybridScore: Float,
        val matchedAtThreshold: Boolean,
    )

    suspend fun calibrate(
        embedder: TextEmbedder,
        samples: List<MemoryEmbeddingGoldenSet.RetrievalSample> =
            MemoryEmbeddingGoldenSet.retrievalSamples,
        candidateThresholds: List<Float> = defaultThresholdCandidates(),
        semanticWeight: Float = MemorySearchScorer.SEMANTIC_WEIGHT,
        tokenWeight: Float = MemorySearchScorer.TOKEN_WEIGHT,
    ): CalibrationResult {
        val latenciesMs = mutableListOf<Long>()
        val scoredSamples = samples.map { sample ->
            val start = System.nanoTime()
            val queryEmbedding = embedder.embed(sample.query)
            val memoryEmbedding = embedder.embed(sample.memory)
            latenciesMs += (System.nanoTime() - start) / 1_000_000L

            val document = MemoryDocumentEntity(
                id = sample.memory.hashCode().toLong(),
                value = sample.memory,
                kind = MemoryDocumentKind.USER_FACT.name,
                category = "FACT",
                source = MemoryDocumentSource.TOOL.name,
                confidence = 0.9f,
                createdAt = 1L,
                updatedAt = 1L,
                embedding = memoryEmbedding?.let { com.example.mydeskrobot.memory.unified.EmbeddingCodec.encode(it) },
            )
            val tokenScore = com.example.mydeskrobot.memory.MemoryTopicMatcher.score(sample.query, sample.memory)
            val semanticScore = if (queryEmbedding != null && memoryEmbedding != null) {
                com.example.mydeskrobot.memory.unified.VectorMath.cosineSimilarity(queryEmbedding, memoryEmbedding)
            } else {
                null
            }
            val hybridScore = when {
                semanticScore != null ->
                    (semanticWeight * semanticScore + tokenWeight * tokenScore).coerceIn(0f, 1f)
                else -> tokenScore
            }
            SampleScore(
                memory = sample.memory,
                query = sample.query,
                shouldMatch = sample.shouldMatch,
                hybridScore = hybridScore,
                matchedAtThreshold = false,
            )
        }

        var bestThreshold = MemorySearchScorer.DEFAULT_MIN_SCORE
        var bestPositiveRecall = 0f
        var bestNegativeReject = 0f

        for (threshold in candidateThresholds) {
            val positive = scoredSamples.filter { it.shouldMatch }
            val negative = scoredSamples.filter { !it.shouldMatch }
            val positiveRecall = if (positive.isEmpty()) {
                1f
            } else {
                positive.count { it.hybridScore >= threshold }.toFloat() / positive.size
            }
            val negativeReject = if (negative.isEmpty()) {
                1f
            } else {
                negative.count { it.hybridScore < threshold }.toFloat() / negative.size
            }
            val better = positiveRecall > bestPositiveRecall ||
                (positiveRecall == bestPositiveRecall && negativeReject > bestNegativeReject)
            if (better) {
                bestThreshold = threshold
                bestPositiveRecall = positiveRecall
                bestNegativeReject = negativeReject
            }
        }

        val scoresAtThreshold = scoredSamples.map {
            it.copy(matchedAtThreshold = it.hybridScore >= bestThreshold)
        }
        val sortedLatencies = latenciesMs.sorted()
        return CalibrationResult(
            recommendedMinScore = bestThreshold,
            positiveRecall = bestPositiveRecall,
            negativeRejectRate = bestNegativeReject,
            embedLatencyMedianMs = percentile(sortedLatencies, 0.50),
            embedLatencyP95Ms = percentile(sortedLatencies, 0.95),
            scoresAtThreshold = scoresAtThreshold,
        )
    }

    fun defaultThresholdCandidates(): List<Float> =
        (20..55 step 1).map { it / 100f }

    private fun percentile(sorted: List<Long>, p: Double): Long {
        if (sorted.isEmpty()) return 0L
        val index = ((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
