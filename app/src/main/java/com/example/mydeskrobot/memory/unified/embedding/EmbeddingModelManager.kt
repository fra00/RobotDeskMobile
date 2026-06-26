package com.example.mydeskrobot.memory.unified.embedding

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads and caches ONNX embedding assets under [modelDir] (first-run, no manual script).
 */
class EmbeddingModelManager(context: Context) {

    val modelDir: File = File(context.applicationContext.filesDir, MODEL_DIR_NAME)

    fun isReady(): Boolean = EmbeddingModelPaths.isReady(modelDir)

    suspend fun ensureDownloaded(): Boolean = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext true
        downloadModel()
    }

    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext true
        try {
            modelDir.mkdirs()
            val baseUrl =
                "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main"
            val downloads = listOf(
                "$baseUrl/tokenizer.json" to EmbeddingModelPaths.TOKENIZER_FILE_NAME,
                "$baseUrl/tokenizer_config.json" to "tokenizer_config.json",
                "$baseUrl/${resolveOnnxPath()}" to EmbeddingModelPaths.MODEL_FILE_NAME,
            )
            for ((url, fileName) in downloads) {
                downloadFile(url, File(modelDir, fileName))
            }
            Log.i(TAG, "Embedding model ready at ${modelDir.absolutePath}")
            isReady()
        } catch (e: Exception) {
            Log.e(TAG, "Embedding model download failed", e)
            false
        }
    }

    private fun resolveOnnxPath(): String {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            primaryAbi.contains("arm64", ignoreCase = true) -> "onnx/model_qint8_arm64.onnx"
            primaryAbi.contains("x86_64", ignoreCase = true) ||
                primaryAbi.contains("x86", ignoreCase = true) -> "onnx/model_quint8_avx2.onnx"
            else -> "onnx/model_qint8_arm64.onnx"
        }
    }

    private fun downloadFile(url: String, dest: File) {
        if (dest.isFile && dest.length() > 0L) return
        dest.parentFile?.mkdirs()
        Log.d(TAG, "Downloading $url -> ${dest.name}")
        URL(url).openStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    companion object {
        private const val TAG = "EmbeddingModelManager"
        private const val MODEL_DIR_NAME = "embedding_model"
    }
}
