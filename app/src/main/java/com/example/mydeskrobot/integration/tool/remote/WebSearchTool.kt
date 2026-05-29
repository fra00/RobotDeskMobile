package com.example.mydeskrobot.integration.tool.remote

import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

/**
 * Web search via pluggable [WebSearchEngine] (default: SearXNG JSON API).
 */
class WebSearchTool(
    private val searchEngine: WebSearchEngine,
) : Tool {

    override val name: String = "web_search"
    override val locality: ToolLocality = ToolLocality.REMOTE

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Search the web for up-to-date information. Returns titles, URLs, and snippets.",
            parameters = listOf(
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "Search query",
                    required = true,
                ),
                ToolParameter(
                    name = "max_results",
                    type = "int",
                    description = "Number of results (1-5, default 3)",
                    required = false,
                ),
            ),
            returns = "results: list of { title, url, snippet }",
            example = """{"name": "web_search", "params": {"query": "notizie ANSA oggi", "max_results": 3}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val query = invocation.params["query"]?.toString()?.trim()
            ?: return ToolResult.Error(
                message = "Parametro 'query' mancante",
                code = "MISSING_PARAM",
            )
        if (query.isBlank()) {
            return ToolResult.Error(
                message = "Query di ricerca vuota",
                code = "MISSING_PARAM",
            )
        }

        val maxResults = parseMaxResults(invocation.params["max_results"])

        return searchEngine.search(query, maxResults).fold(
            onSuccess = { hits ->
                val results = hits.map { hit ->
                    mapOf(
                        "title" to hit.title,
                        "url" to hit.url,
                        "snippet" to hit.snippet,
                    )
                }
                ToolResult.Success(
                    data = mapOf(
                        "query" to query,
                        "results" to results,
                    ),
                )
            },
            onFailure = { error ->
                ToolResult.Error(
                    message = "Ricerca web non riuscita: ${error.message ?: "errore sconosciuto"}",
                    code = "SEARCH_FAILED",
                    recoverable = true,
                )
            },
        )
    }

    private fun parseMaxResults(raw: Any?): Int {
        val value = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: return DEFAULT_MAX_RESULTS
        return value.coerceIn(1, 5)
    }

    companion object {
        private const val DEFAULT_MAX_RESULTS = 3
    }
}
