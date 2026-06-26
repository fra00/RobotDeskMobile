package com.example.mydeskrobot.memory.unified.embedding

import java.io.BufferedReader
import java.io.InputStream
import java.io.Reader

/**
 * Minimal BERT WordPiece tokenizer for sentence-transformers ONNX models.
 * Loads vocab.txt (line index = token id).
 */
class BertWordPieceTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val maxLength: Int,
    private val clsId: Int,
    private val sepId: Int,
    private val padId: Int,
    private val unkId: Int,
) : EmbeddingTokenizer {

    override fun encode(text: String): EmbeddingTokenizer.Encoding {
        val tokens = mutableListOf<Int>()
        tokens += clsId
        for (basic in basicTokenize(text)) {
            tokens += wordPiece(basic)
        }
        tokens += sepId

        val truncated = tokens.take(maxLength).toMutableList()
        val attentionMask = LongArray(maxLength)
        val inputIds = LongArray(maxLength)
        val tokenTypeIds = LongArray(maxLength)
        for (i in 0 until maxLength) {
            if (i < truncated.size) {
                inputIds[i] = truncated[i].toLong()
                attentionMask[i] = 1L
                tokenTypeIds[i] = 0L
            } else {
                inputIds[i] = padId.toLong()
                attentionMask[i] = 0L
                tokenTypeIds[i] = 0L
            }
        }
        return EmbeddingTokenizer.Encoding(inputIds, attentionMask, tokenTypeIds)
    }

    private fun basicTokenize(text: String): List<String> {
        val normalized = cleanText(text.lowercase())
        if (normalized.isBlank()) return emptyList()
        return normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private fun cleanText(text: String): String =
        buildString(text.length) {
            var previousIsWhitespace = true
            for (ch in text) {
                when {
                    ch.isLetterOrDigit() -> {
                        append(ch)
                        previousIsWhitespace = false
                    }
                    ch.isWhitespace() -> {
                        if (!previousIsWhitespace) append(' ')
                        previousIsWhitespace = true
                    }
                    else -> {
                        if (!previousIsWhitespace) append(' ')
                        append(ch)
                        if (!ch.isWhitespace()) previousIsWhitespace = false
                    }
                }
            }
        }.trim()

    private fun wordPiece(token: String): List<Int> {
        if (token.isEmpty()) return listOf(unkId)
        if (vocab.containsKey(token)) return listOf(vocab.getValue(token))

        val chars = token.toCharArray()
        var start = 0
        val pieces = mutableListOf<Int>()
        while (start < chars.size) {
            var end = chars.size
            var found: String? = null
            while (start < end) {
                val sub = chars.copyOfRange(start, end).concatToString()
                val candidate = if (start == 0) sub else "##$sub"
                if (vocab.containsKey(candidate)) {
                    found = candidate
                    break
                }
                end--
            }
            if (found == null) return listOf(unkId)
            pieces += vocab.getValue(found)
            start = end
        }
        return pieces
    }

    companion object {
        const val DEFAULT_MAX_LENGTH = 128
        private const val DEFAULT_CLS_ID = 101
        private const val DEFAULT_SEP_ID = 102
        private const val DEFAULT_PAD_ID = 0
        private const val DEFAULT_UNK_ID = 100

        fun fromVocabReader(
            reader: Reader,
            maxLength: Int = DEFAULT_MAX_LENGTH,
        ): BertWordPieceTokenizer {
            val vocab = linkedMapOf<String, Int>()
            reader.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val token = line.trim()
                    if (token.isNotEmpty()) vocab[token] = index
                }
            }
            require(vocab.isNotEmpty()) { "vocab.txt is empty" }
            return BertWordPieceTokenizer(
                vocab = vocab,
                maxLength = maxLength,
                clsId = vocab["[CLS]"] ?: DEFAULT_CLS_ID,
                sepId = vocab["[SEP]"] ?: DEFAULT_SEP_ID,
                padId = vocab["[PAD]"] ?: vocab[""] ?: DEFAULT_PAD_ID,
                unkId = vocab["[UNK]"] ?: DEFAULT_UNK_ID,
            )
        }

        fun fromVocabStream(stream: InputStream, maxLength: Int = DEFAULT_MAX_LENGTH): BertWordPieceTokenizer =
            fromVocabReader(BufferedReader(stream.reader()), maxLength)
    }
}
