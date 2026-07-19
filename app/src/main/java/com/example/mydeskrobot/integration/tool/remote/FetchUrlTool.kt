package com.example.mydeskrobot.integration.tool.remote

import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches a web page and returns plain article text for the LLM (Readability4J + legacy fallback).
 */
class FetchUrlTool(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val articleExtractor: WebArticleExtractor = WebArticleExtractor(),
) : Tool {

    override val name: String = "fetch_url"
    override val locality: ToolLocality = ToolLocality.REMOTE

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Read a web page and return its main article text (no HTML). Use after web_search or when the user gives a URL.",
            parameters = listOf(
                ToolParameter(
                    name = "url",
                    type = "string",
                    description = "Full https URL of the page",
                    required = true,
                ),
                ToolParameter(
                    name = "max_chars",
                    type = "int",
                    description = "Max characters of extracted text returned to the LLM (default 3500, max 4500)",
                    required = false,
                ),
                ToolParameter(
                    name = "start_char",
                    type = "int",
                    description = "Character offset into the full extracted article (default 0; use for next chunk when truncated)",
                    required = false,
                ),
            ),
            returns = "title, content, excerpt, chars_total, chars_returned, start_char, truncated, extractor",
            example = """{"name": "fetch_url", "params": {"url": "https://www.ansa.it/...", "max_chars": 4000}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val rawUrl = invocation.params["url"]?.toString()?.trim()
            ?: return ToolResult.Error(
                message = "Parametro 'url' mancante",
                code = "MISSING_PARAM",
            )

        val uriResult = UrlSafety.validateHttpUrl(rawUrl)
        val uri = uriResult.getOrElse { error ->
            return ToolResult.Error(
                message = error.message ?: "URL non consentito",
                code = "INVALID_URL",
            )
        }

        val maxChars = FetchUrlContentSlice.parseMaxChars(invocation.params["max_chars"])
        val startChar = FetchUrlContentSlice.parseStartChar(invocation.params["start_char"])
        val fetchUrl = uri.toString()

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(fetchUrl)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext ToolResult.Error(
                        message = "Impossibile scaricare la pagina: HTTP ${response.code}",
                        code = "HTTP_ERROR",
                        recoverable = true,
                    )
                }

                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.size > MAX_DOWNLOAD_BYTES) {
                    return@withContext ToolResult.Error(
                        message = "Pagina troppo grande per il download (${bytes.size / 1024} KB, max ${MAX_DOWNLOAD_BYTES / 1024} KB)",
                        code = "PAGE_TOO_LARGE",
                        recoverable = true,
                    )
                }

                val charset = response.body?.contentType()?.charset() ?: Charsets.UTF_8
                val html = String(bytes, charset)
                val extraction = articleExtractor.extract(fetchUrl, html)
                val slice = FetchUrlContentSlice.slice(extraction.fullText, startChar, maxChars)

                if (slice.content.isBlank()) {
                    return@withContext ToolResult.Error(
                        message = "Nessun testo leggibile nella pagina (potrebbe richiedere JavaScript)",
                        code = "EMPTY_CONTENT",
                        recoverable = true,
                    )
                }

                val title = extraction.title.ifBlank { fetchUrl }

                ToolResult.Success(
                    data = buildMap {
                        put("url", fetchUrl)
                        put("title", title)
                        put("content", slice.content)
                        extraction.excerpt?.let { put("excerpt", it) }
                        put("chars_total", slice.charsTotal)
                        put("chars_returned", slice.charsReturned)
                        put("start_char", slice.startChar)
                        put("truncated", slice.truncated)
                        put("extractor", extraction.extractor.wireName)
                    },
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Errore lettura pagina: ${e.message}",
                    code = "FETCH_ERROR",
                    recoverable = true,
                )
            }
        }
    }

    companion object {
        /** Full HTML download cap; extraction runs on complete document, text-only truncated for LLM. */
        private const val MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024
        private const val USER_AGENT = "MyDeskRobot/1.0 (Android; fetch_url)"

        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
}
