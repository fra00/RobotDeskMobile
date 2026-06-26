package com.example.mydeskrobot.memory.unified.embedding

import android.content.Context
import java.io.File

object TextEmbedderFactory {

    fun create(context: Context): TextEmbedder {
        val modelDir = EmbeddingModelPaths.resolveModelDir(context.applicationContext) ?: return NoOpTextEmbedder
        return createForDirectory(modelDir)
    }

    fun createForDirectory(modelDir: File): TextEmbedder {
        if (!EmbeddingModelPaths.isReady(modelDir)) return NoOpTextEmbedder
        return runCatching {
            val tokenizer = loadTokenizer(modelDir)
            OnnxTextEmbedder(
                modelFilePath = EmbeddingModelPaths.modelFile(modelDir).absolutePath,
                tokenizer = tokenizer,
            )
        }.getOrElse { NoOpTextEmbedder }
    }

    private fun loadTokenizer(modelDir: File): EmbeddingTokenizer {
        if (EmbeddingModelPaths.tokenizerFile(modelDir).isFile) {
            return HfTokenizerAdapter.fromDirectory(modelDir)
        }
        return BertWordPieceTokenizer.fromVocabStream(
            EmbeddingModelPaths.vocabFile(modelDir).inputStream(),
        )
    }
}
