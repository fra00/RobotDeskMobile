package com.example.mydeskrobot.memory.unified.embedding

import com.example.mydeskrobot.memory.MemoryTopicMatcher
import com.example.mydeskrobot.memory.unified.MemorySearchScorer
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGoldenSetTokenBaselineTest {

    @Test
    fun tokenBaseline_recalls_known_paraphrases() {
        var hits = 0
        for ((memory, query) in MemoryEmbeddingGoldenSet.tokenBaselineQueries) {
            val score = MemoryTopicMatcher.score(query, memory)
            if (score >= MemorySearchScorer.DEFAULT_MIN_SCORE) hits++
        }
        val recall = hits.toFloat() / MemoryEmbeddingGoldenSet.tokenBaselineQueries.size
        assertTrue(
            "Token baseline recall $recall below 60% on golden subset",
            recall >= 0.60f,
        )
    }

    @Test
    fun tokenBaseline_rejects_garibaldi_noise_at_hybrid_min_score() {
        val score = MemoryTopicMatcher.score(
            query = "chi era Garibaldi",
            memoryValue = "Il cane si chiama Brina",
        )
        assertTrue(score < MemorySearchScorer.HYBRID_MIN_SCORE)
    }
}
