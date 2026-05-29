package com.example.mydeskrobot.integration.tool.remote

/**
 * Pluggable web search backend for [WebSearchTool].
 * Current implementation: [SearxngWebSearchEngine].
 */
fun interface WebSearchEngine {
    suspend fun search(query: String, maxResults: Int): Result<List<WebSearchHit>>
}

data class WebSearchHit(
    val title: String,
    val url: String,
    val snippet: String,
)
