package com.example.mydeskrobot.memory.unified.embedding

import com.example.mydeskrobot.BuildConfig
import android.content.Context
import java.io.File
import java.util.Properties

object EmbeddingModelPaths {

    const val ASSET_DIR = "models/embedding"
    const val MODEL_FILE_NAME = "model.onnx"
    const val TOKENIZER_FILE_NAME = "tokenizer.json"
    const val VOCAB_FILE_NAME = "vocab.txt"
    const val JVM_MODEL_ENV = "EMBEDDING_MODEL_DIR"
    const val LOCAL_PROPERTY_KEY = "embedding.model.dir"

    fun resolveModelDir(context: Context? = null): File? {
        System.getenv(JVM_MODEL_ENV)?.let { path ->
            val dir = File(path)
            if (isReady(dir)) return dir
        }
        readProjectLocalPropertyDir()?.let { dir ->
            if (isReady(dir)) return dir
        }
        val buildConfigDir = BuildConfig.EMBEDDING_MODEL_DIR.trim()
        if (buildConfigDir.isNotEmpty()) {
            val dir = File(buildConfigDir)
            if (isReady(dir)) return dir
        }
        context?.let { ctx ->
            val filesDir = File(ctx.filesDir, "embedding_model")
            if (isReady(filesDir)) return filesDir
        }
        return null
    }

    fun isReady(dir: File): Boolean {
        val hasModel = File(dir, MODEL_FILE_NAME).isFile
        val hasTokenizer = File(dir, TOKENIZER_FILE_NAME).isFile || File(dir, VOCAB_FILE_NAME).isFile
        return hasModel && hasTokenizer
    }

    fun modelFile(dir: File): File = File(dir, MODEL_FILE_NAME)

    fun tokenizerFile(dir: File): File = File(dir, TOKENIZER_FILE_NAME)

    fun vocabFile(dir: File): File = File(dir, VOCAB_FILE_NAME)

    private fun readProjectLocalPropertyDir(): File? {
        val projectRoot = System.getProperty("user.dir") ?: return null
        val localProps = File(projectRoot, "local.properties")
        if (!localProps.isFile) return null
        val properties = Properties()
        localProps.inputStream().use { properties.load(it) }
        val path = properties.getProperty(LOCAL_PROPERTY_KEY)?.trim().orEmpty()
        if (path.isBlank()) return null
        return File(path)
    }
}
