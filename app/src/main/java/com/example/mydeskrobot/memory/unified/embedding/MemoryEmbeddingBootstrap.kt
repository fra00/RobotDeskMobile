package com.example.mydeskrobot.memory.unified.embedding

import android.content.Context
import android.util.Log
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background first-run download + backfill of missing document embeddings.
 */
object MemoryEmbeddingBootstrap {

    private const val TAG = "MemoryEmbeddingBootstrap"

    fun start(
        context: Context,
        scope: CoroutineScope,
        unifiedMemoryRepository: UnifiedMemoryRepository,
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val ready = EmbeddingRuntime.ensureReady(context)
                if (!ready) {
                    Log.i(TAG, "Embedding model not ready; token-only recall remains active")
                    return@launch
                }
                var total = 0
                var batch: Int
                do {
                    batch = unifiedMemoryRepository.reindexMissingEmbeddings(limit = 32)
                    total += batch
                } while (batch > 0)
                if (total > 0) {
                    Log.i(TAG, "Backfilled embeddings for $total documents")
                }
            }.onFailure { error ->
                Log.w(TAG, "Embedding bootstrap failed", error)
            }
        }
    }
}
