package com.example.mydeskrobot.memory.unified.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.LongBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ONNX Runtime sentence embedder for paraphrase-multilingual-MiniLM-L12-v2 exports.
 * Expects inputs: input_ids, attention_mask, optional token_type_ids. Output: sentence_embedding (384-dim).
 */
class OnnxTextEmbedder(
    modelFilePath: String,
    tokenizer: EmbeddingTokenizer,
    private val embeddingDim: Int = 384,
) : TextEmbedder, Closeable {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(
        modelFilePath,
        OrtSession.SessionOptions(),
    )
    private val tokenizerRef = tokenizer
    private val inputNames: Set<String> = session.inputNames.toSet()
    private val outputName: String = session.outputNames.firstOrNull { it == "sentence_embedding" }
        ?: session.outputNames.firstOrNull { it.contains("sentence", ignoreCase = true) }
        ?: session.outputNames.last()

    override val isAvailable: Boolean = true
    override val embeddingDimension: Int = embeddingDim

    override suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.Default) {
        val normalized = text.trim()
        if (normalized.isBlank()) return@withContext null
        val encoding = tokenizerRef.encode(normalized)
        val shape = longArrayOf(1L, encoding.inputIds.size.toLong())
        val tensors = mutableListOf<OnnxTensor>()
        try {
            val runInputs = linkedMapOf<String, OnnxTensor>()
            if ("input_ids" in inputNames) {
                OnnxTensor.createTensor(environment, LongBuffer.wrap(encoding.inputIds), shape).also {
                    tensors += it
                    runInputs["input_ids"] = it
                }
            }
            if ("attention_mask" in inputNames) {
                OnnxTensor.createTensor(environment, LongBuffer.wrap(encoding.attentionMask), shape).also {
                    tensors += it
                    runInputs["attention_mask"] = it
                }
            }
            if ("token_type_ids" in inputNames) {
                OnnxTensor.createTensor(environment, LongBuffer.wrap(encoding.tokenTypeIds), shape).also {
                    tensors += it
                    runInputs["token_type_ids"] = it
                }
            }
            session.run(runInputs).use { outputs ->
                val embeddingTensor = outputs.get(outputName).get() as OnnxTensor
                val buffer = embeddingTensor.floatBuffer
                val vector = FloatArray(embeddingDim)
                buffer.get(vector)
                vector
            }
        } finally {
            tensors.forEach { it.close() }
        }
    }

    override fun close() {
        session.close()
        environment.close()
    }
}
