package com.example.mydeskrobot.memory.unified.embedding

import android.content.Context
import java.io.Closeable

/**
 * Lazy singleton for on-device embedding model + [TextEmbedder].
 * Falls back to [NoOpTextEmbedder] when model is missing or load fails.
 */
object EmbeddingRuntime {

    @Volatile
    private var manager: EmbeddingModelManager? = null

    @Volatile
    private var cachedEmbedder: TextEmbedder? = null

    fun getManager(context: Context): EmbeddingModelManager {
        return manager ?: synchronized(this) {
            manager ?: EmbeddingModelManager(context.applicationContext).also { manager = it }
        }
    }

    fun getEmbedder(context: Context): TextEmbedder {
        cachedEmbedder?.let { if (it.isAvailable) return it }
        return synchronized(this) {
            cachedEmbedder?.takeIf { it.isAvailable } ?: loadEmbedder(context).also {
                cachedEmbedder = it
            }
        }
    }

    suspend fun ensureReady(context: Context): Boolean {
        val ready = getManager(context).ensureDownloaded()
        if (ready) {
            synchronized(this) {
                (cachedEmbedder as? Closeable)?.close()
                cachedEmbedder = null
            }
        }
        return ready && getEmbedder(context).isAvailable
    }

    fun invalidateEmbedder() {
        synchronized(this) {
            (cachedEmbedder as? Closeable)?.close()
            cachedEmbedder = null
        }
    }

    private fun loadEmbedder(context: Context): TextEmbedder {
        val appContext = context.applicationContext
        EmbeddingModelPaths.resolveModelDir(appContext)?.let { dir ->
            TextEmbedderFactory.createForDirectory(dir).takeIf { it.isAvailable }?.let { return it }
        }
        val managedDir = getManager(appContext).modelDir
        if (EmbeddingModelPaths.isReady(managedDir)) {
            TextEmbedderFactory.createForDirectory(managedDir).takeIf { it.isAvailable }?.let { return it }
        }
        return NoOpTextEmbedder
    }
}
