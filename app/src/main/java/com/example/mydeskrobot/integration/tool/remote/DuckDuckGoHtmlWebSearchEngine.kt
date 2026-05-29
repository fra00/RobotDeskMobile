package com.example.mydeskrobot.integration.tool.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Fallback search via DuckDuckGo HTML lite (no API key).
 * POST https://html.duckduckgo.com/html/
 */
class DuckDuckGoHtmlWebSearchEngine(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : WebSearchEngine {

    override suspend fun search(query: String, maxResults: Int): Result<List<WebSearchHit>> {
        val limit = maxResults.coerceIn(1, MAX_RESULTS)
        return withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("q", query)
                    .build()
                val request = Request.Builder()
                    .url(SEARCH_URL)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("DuckDuckGo HTTP ${response.code}"),
                    )
                }
                val html = response.body?.string()
                    ?: return@withContext Result.failure(
                        IllegalStateException("Risposta DuckDuckGo vuota"),
                    )

                val hits = parseResults(html, limit)
                if (hits.isEmpty()) {
                    Result.failure(IllegalStateException("Nessun risultato DuckDuckGo"))
                } else {
                    Result.success(hits)
                }
            } catch (e: Exception) {
                Log.e(TAG, "DuckDuckGo search error", e)
                Result.failure(e)
            }
        }
    }

    internal fun parseResults(html: String, limit: Int): List<WebSearchHit> {
        val doc = Jsoup.parse(html)
        return doc.select("div.result").asSequence()
            .mapNotNull { row -> parseRow(row) }
            .take(limit)
            .toList()
    }

    private fun parseRow(row: Element): WebSearchHit? {
        val link = row.selectFirst("a.result__a") ?: return null
        val rawUrl = link.attr("href").trim()
        val url = normalizeResultUrl(rawUrl) ?: return null
        val title = link.text().trim().ifBlank { url }
        val snippet = row.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
        return WebSearchHit(title = title, url = url, snippet = snippet)
    }

    internal fun normalizeResultUrl(raw: String): String? {
        if (raw.isBlank()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        if (raw.startsWith("//")) return "https:$raw"
        if (raw.contains("uddg=")) {
            val encoded = raw.substringAfter("uddg=", "")
                .substringBefore("&")
            if (encoded.isNotBlank()) {
                return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
            }
        }
        return null
    }

    companion object {
        private const val TAG = "DdgHtmlSearch"
        private const val SEARCH_URL = "https://html.duckduckgo.com/html/"
        private const val MAX_RESULTS = 10
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
}
