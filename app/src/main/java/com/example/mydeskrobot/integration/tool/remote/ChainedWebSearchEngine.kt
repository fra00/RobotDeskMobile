package com.example.mydeskrobot.integration.tool.remote

import android.util.Log

/**
 * Tries multiple [WebSearchEngine] implementations until one succeeds.
 */
class ChainedWebSearchEngine(
    private val engines: List<WebSearchEngine>,
) : WebSearchEngine {

    override suspend fun search(query: String, maxResults: Int): Result<List<WebSearchHit>> {
        var lastError: Throwable? = null
        for (engine in engines) {
            val result = engine.search(query, maxResults)
            if (result.isSuccess) {
                Log.i(TAG, "web_search ok via ${engine.javaClass.simpleName}")
                return result
            }
            lastError = result.exceptionOrNull()
            Log.w(
                TAG,
                "web_search failed via ${engine.javaClass.simpleName}: ${lastError?.message}",
            )
        }
        return Result.failure(
            lastError ?: IllegalStateException("Nessun motore di ricerca disponibile"),
        )
    }

    companion object {
        private const val TAG = "ChainedWebSearch"
    }
}
