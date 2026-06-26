package com.example.mydeskrobot.memory.unified.embedding

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import java.io.File

/**
 * HuggingFace tokenizer.json adapter (SentencePiece / Unigram — multilingual MiniLM).
 */
class HfTokenizerAdapter private constructor(
    private val tokenizer: HuggingFaceTokenizer,
    private val maxLength: Int,
) : EmbeddingTokenizer {

    override fun encode(text: String): EmbeddingTokenizer.Encoding {
        val encoded = tokenizer.encode(text)
        val ids = encoded.ids
        val mask = encoded.attentionMask
        val inputIds = LongArray(maxLength)
        val attentionMask = LongArray(maxLength)
        val tokenTypeIds = LongArray(maxLength)
        for (i in 0 until maxLength) {
            if (i < ids.size) {
                inputIds[i] = ids[i]
                attentionMask[i] = if (i < mask.size) mask[i] else 1L
                tokenTypeIds[i] = if (i < encoded.typeIds.size) encoded.typeIds[i] else 0L
            } else {
                inputIds[i] = 0L
                attentionMask[i] = 0L
                tokenTypeIds[i] = 0L
            }
        }
        return EmbeddingTokenizer.Encoding(inputIds, attentionMask, tokenTypeIds)
    }

    fun close() {
        tokenizer.close()
    }

    companion object {
        fun fromDirectory(modelDir: File, maxLength: Int = BertWordPieceTokenizer.DEFAULT_MAX_LENGTH): HfTokenizerAdapter {
            val tokenizerFile = EmbeddingModelPaths.tokenizerFile(modelDir)
            require(tokenizerFile.isFile) { "Missing ${EmbeddingModelPaths.TOKENIZER_FILE_NAME} in $modelDir" }
            val hfTokenizer = HuggingFaceTokenizer.newInstance(
                tokenizerFile.toPath(),
                mapOf(
                    "maxLength" to maxLength.toString(),
                    "padding" to "true",
                    "truncation" to "true",
                ),
            )
            return HfTokenizerAdapter(hfTokenizer, maxLength)
        }
    }
}
