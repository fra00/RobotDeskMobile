package com.example.mydeskrobot.memory.unified.embedding

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * ONNX benchmark — runs only when model files are present.
 *
 * Set env `EMBEDDING_MODEL_DIR` or `embedding.model.dir` in local.properties
 * to a folder containing model.onnx + tokenizer.json.
 */
class MemoryEmbeddingBenchmarkTest {

    @Test
    fun paraphrase_gate_passes_on_italian_pairs() = runTest {
        val embedder = loadEmbedderOrSkip()
        val gate = EmbeddingQualityGate.verifyParaphrasePairs(embedder)
        embedder.close()
        if (!gate.passed) {
            val details = gate.pairScores.joinToString("\n") { (pair, score) ->
                "${pair.memory} ↔ ${pair.query}: $score"
            }
            throw AssertionError(
                "Paraphrase gate failed (min ${EmbeddingQualityGate.MIN_PARAPHRASE_COSINE}):\n$details",
            )
        }
    }

    @Test
    fun calibrator_reports_hybrid_threshold_and_latency() = runTest {
        val embedder = loadEmbedderOrSkip()
        embedder.embed("warmup per benchmark latenza")
        val calibration = MemorySearchCalibrator.calibrate(embedder)
        embedder.close()

        println(
            buildString {
                appendLine("=== Memory embedding calibration ===")
                appendLine("recommendedMinScore=${calibration.recommendedMinScore}")
                appendLine("positiveRecall=${calibration.positiveRecall}")
                appendLine("negativeRejectRate=${calibration.negativeRejectRate}")
                appendLine("embedLatencyMedianMs=${calibration.embedLatencyMedianMs}")
                appendLine("embedLatencyP95Ms=${calibration.embedLatencyP95Ms}")
                calibration.scoresAtThreshold.forEach { sample ->
                    appendLine(
                        "${sample.query} -> ${sample.hybridScore} " +
                            "(expect=${sample.shouldMatch}, matched=${sample.matchedAtThreshold})",
                    )
                }
            },
        )

        require(calibration.positiveRecall >= 0.80f) {
            "Positive recall too low: ${calibration.positiveRecall}"
        }
        require(calibration.negativeRejectRate >= 0.75f) {
            "Negative reject rate too low: ${calibration.negativeRejectRate}"
        }
        require(calibration.embedLatencyP95Ms <= 3_000L) {
            "Embed p95 latency too high on JVM: ${calibration.embedLatencyP95Ms}ms (target device p95 ≤ 50ms)"
        }
    }

    private fun loadEmbedderOrSkip(): TextEmbedder {
        val modelDir = EmbeddingModelPaths.resolveModelDir()
        assumeTrue(
            "Skipping ONNX benchmark: set EMBEDDING_MODEL_DIR or embedding.model.dir",
            modelDir != null,
        )
        val embedder = TextEmbedderFactory.createForDirectory(modelDir!!)
        assumeTrue(
            "Skipping ONNX benchmark: model failed to load from $modelDir",
            embedder.isAvailable,
        )
        return embedder
    }
}
