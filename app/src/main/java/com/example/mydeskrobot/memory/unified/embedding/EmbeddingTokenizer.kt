package com.example.mydeskrobot.memory.unified.embedding

/**
 * Tokenizer output for ONNX sentence embedding models.
 */
interface EmbeddingTokenizer {

    data class Encoding(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        /** Segment ids; all-zero for single-sentence embedding. */
        val tokenTypeIds: LongArray,
    )

    fun encode(text: String): Encoding
}
