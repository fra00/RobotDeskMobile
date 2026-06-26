package com.example.mydeskrobot.memory.unified.embedding

import com.example.mydeskrobot.memory.unified.VectorMath
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySearchCalibratorTest {

    @Test
    fun calibrate_prefers_threshold_that_keeps_positives_and_drops_negatives() = runTest {
        val embedder = FakeDirectionalEmbedder(
            anchors = mapOf(
                "work" to floatArrayOf(1f, 0f, 0f),
                "dog" to floatArrayOf(0f, 1f, 0f),
                "history" to floatArrayOf(0f, 0f, 1f),
                "weather" to floatArrayOf(0f, 0f, -1f),
            ),
            routing = mapOf(
                "Il venerdì lavora dalle 9 alle 13" to "work",
                "quando lavoro il venerdì" to "work",
                "Il cane si chiama Brina" to "dog",
                "come si chiama il mio animale" to "dog",
                "chi era Garibaldi" to "history",
                "meteo domani" to "weather",
                "L'utente lavora con C# su TeamSystem" to "work",
                "Promemoria: prendere le medicine alle 20" to "dog",
                "orari di lavoro" to "work",
                "Ogni mattina fa colazione alle 8" to "work",
                "abitudini mattutine utente" to "work",
                "Lun-gio lavora anche il pomeriggio 14-18" to "work",
                "orari pomeridiani settimana" to "work",
                "L'utente fa smart working il martedì" to "work",
                "lavori da casa" to "work",
                "La mamma si chiama Anna" to "dog",
            ),
        )

        val result = MemorySearchCalibrator.calibrate(embedder)

        assertTrue(result.positiveRecall >= 0.85f)
        assertTrue(result.negativeRejectRate >= 0.75f)
        assertTrue(result.recommendedMinScore in 0.20f..0.80f)
    }

    private class FakeDirectionalEmbedder(
        private val anchors: Map<String, FloatArray>,
        private val routing: Map<String, String>,
    ) : TextEmbedder {
        override val isAvailable: Boolean = true
        override val embeddingDimension: Int = 3

        override suspend fun embed(text: String): FloatArray? {
            val key = routing[text.trim()] ?: return null
            return anchors[key]?.let { VectorMath.normalize(it) }
        }

        override fun close() = Unit
    }
}
