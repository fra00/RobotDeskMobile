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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Fetches a web page and returns plain text (HTML stripped) for the LLM.
 */
class FetchUrlTool(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : Tool {

    override val name: String = "fetch_url"
    override val locality: ToolLocality = ToolLocality.REMOTE

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Read a web page and return its main text content (no HTML). Use after web_search or when the user gives a URL.",
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
                    description = "Max characters of extracted text (default 2000)",
                    required = false,
                ),
            ),
            returns = "title (string), content (string)",
            example = """{"name": "fetch_url", "params": {"url": "https://www.ansa.it/...", "max_chars": 2000}, "await_result": true}""",
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

        val maxChars = parseMaxChars(invocation.params["max_chars"])
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
                        message = "Pagina troppo grande",
                        code = "PAGE_TOO_LARGE",
                    )
                }

                val charset = response.body?.contentType()?.charset() ?: Charsets.UTF_8
                val html = String(bytes, charset)
                val doc = Jsoup.parse(html, fetchUrl)
                val title = doc.title().trim()
                val content = extractText(doc).take(maxChars)

                if (content.isBlank()) {
                    return@withContext ToolResult.Error(
                        message = "Nessun testo leggibile nella pagina (potrebbe richiedere JavaScript)",
                        code = "EMPTY_CONTENT",
                        recoverable = true,
                    )
                }

                ToolResult.Success(
                    data = mapOf(
                        "url" to fetchUrl,
                        "title" to title,
                        "content" to content,
                    ),
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

    private fun extractText(doc: Document): String {
        doc.select("script, style, nav, footer, header, aside, noscript").remove()
        val main = doc.selectFirst("article, main, [role=main]")
        val source = main ?: doc.body()
        return source?.text()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    }

    private fun parseMaxChars(raw: Any?): Int {
        val value = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: return DEFAULT_MAX_CHARS
        return value.coerceIn(200, 8000)
    }

    companion object {
        private const val DEFAULT_MAX_CHARS = 2000
        private const val MAX_DOWNLOAD_BYTES = 512 * 1024
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
